const VideoResolver = require("./VideoResolver");
const { ErrorCode, createSuccessResult, createErrorResult } = require("../types/ResolveResult");
const SafeLogger = require("../logger/safeLogger");

/**
 * 降级解析器：轻量 HTTP 请求分享页（仅作为 BrowserResolver 故障时的备用 Fallback）
 */
class LegacySharePageResolver extends VideoResolver {
  constructor() {
    super("LegacySharePageResolver");
  }

  canResolve(url) {
    if (!url || typeof url !== "string") return false;
    return url.includes("douyin.com");
  }

  async resolve(url) {
    const startTime = Date.now();
    SafeLogger.info(this.name, "触发降级解析请求", { url });

    try {
      // 1. 跟随短链接 302
      const redirectResp = await fetch(url, {
        method: "GET",
        redirect: "follow",
        headers: {
          "User-Agent":
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
      });

      const finalUrl = redirectResp.url;
      const status = redirectResp.status;
      const contentType = redirectResp.headers.get("content-type") || "";
      const text = await redirectResp.text();
      const durationMs = Date.now() - startTime;

      SafeLogger.info(this.name, "降级 HTTP 响应接收完成", {
        status,
        contentType,
        length: text.length,
        finalUrl,
        durationMs,
        htmlSnippet: text.slice(0, 200)
      });

      if (status === 403) {
        return createErrorResult({
          error_code: ErrorCode.ACCESS_DENIED,
          message: "访问被平台安全机制拒绝 (HTTP 403)"
        });
      }

      if (status === 429) {
        return createErrorResult({
          error_code: ErrorCode.RATE_LIMITED,
          message: "请求过频，触发限流 (HTTP 429)"
        });
      }

      // 诊断是否为仅含 CSP / 验证脚本的防爬 HTML
      if (
        text.includes("argus-csp-token") ||
        text.includes("data-sdk-glue-in") ||
        text.includes("secsdk")
      ) {
        SafeLogger.warn(this.name, "降级页面检测到 Argus 安全防护拦截", {
          htmlSnippet: text.slice(0, 200)
        });
        return createErrorResult({
          error_code: ErrorCode.HTML_NOT_DATA,
          message: "抖音要求网页环境验证，请稍后重试"
        });
      }

      // 尝试提取 _ROUTER_DATA / RENDER_DATA
      const match = text.match(/window\._ROUTER_DATA\s*=\s*([\s\S]*?);<\/script>/);
      if (match && match[1]) {
        try {
          const data = JSON.parse(match[1]);
          const aweme = this._findAweme(data);
          if (aweme) {
            return this._buildResult(aweme, finalUrl);
          }
        } catch (_e) {}
      }

      return createErrorResult({
        error_code: ErrorCode.HTML_NOT_DATA,
        message: "未能在旧版分享页提取到数据结构 (HTML_NOT_DATA)"
      });
    } catch (err) {
      SafeLogger.error(this.name, "降级请求网络异常", { error: err.message });
      return createErrorResult({
        error_code: ErrorCode.NETWORK_ERROR,
        message: `网络连接异常: ${err.message}`
      });
    }
  }

  _findAweme(obj) {
    if (!obj || typeof obj !== "object") return null;
    if (obj.aweme_detail && obj.aweme_detail.video) return obj.aweme_detail;
    if (obj.video && obj.desc) return obj;
    for (const k of Object.keys(obj)) {
      const res = this._findAweme(obj[k]);
      if (res) return res;
    }
    return null;
  }

  _buildResult(aweme, url) {
    const id = aweme.aweme_id || "legacy_" + Date.now();
    const title = aweme.desc || "抖音作品";
    const author = aweme.author ? (aweme.author.nickname || "抖音作者") : "抖音作者";
    const coverUrl = aweme.video && aweme.video.cover && aweme.video.cover.url_list ? aweme.video.cover.url_list[0] : "";
    const rawPlayUrl = aweme.video && aweme.video.play_addr && aweme.video.play_addr.url_list ? aweme.video.play_addr.url_list[0] : "";

    if (!rawPlayUrl) {
      return createErrorResult({
        error_code: ErrorCode.MEDIA_URL_NOT_FOUND,
        message: "未找到视频播放直链"
      });
    }

    return createSuccessResult({
      id,
      title,
      author,
      cover_url: coverUrl,
      media: [
        {
          type: "video",
          url: rawPlayUrl.replace("playwm", "play"),
          mime: "video/mp4"
        }
      ]
    });
  }
}

module.exports = LegacySharePageResolver;
