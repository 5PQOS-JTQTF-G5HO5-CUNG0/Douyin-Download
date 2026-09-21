const { chromium } = require("playwright");
const VideoResolver = require("./VideoResolver");
const { ErrorCode, createSuccessResult, createErrorResult } = require("../types/ResolveResult");
const SafeLogger = require("../logger/safeLogger");

/**
 * 主解析器：基于真实无头浏览器环境监听并提取抖音作品
 */
class DouyinBrowserResolver extends VideoResolver {
  constructor() {
    super("DouyinBrowserResolver");
    this.browser = null;
    this.isInitializing = false;
  }

  canResolve(url) {
    if (!url || typeof url !== "string") return false;
    const lower = url.toLowerCase();
    return lower.includes("douyin.com") || lower.includes("iesdouyin.com");
  }

  async _getBrowser() {
    if (!this.browser) {
      if (this.isInitializing) {
        while (this.isInitializing) {
          await new Promise(r => setTimeout(r, 100));
        }
        return this.browser;
      }
      this.isInitializing = true;
      try {
        SafeLogger.info(this.name, "正在启动 Chromium 浏览器实例...");
        this.browser = await chromium.launch({
          headless: true,
          args: [
            "--no-sandbox",
            "--disable-setuid-sandbox",
            "--disable-blink-features=AutomationControlled",
            "--disable-infobars",
            "--window-size=412,915"
          ]
        });
        SafeLogger.info(this.name, "Chromium 启动完成");
      } catch (err) {
        SafeLogger.error(this.name, "Chromium 启动失败", { error: err.message });
        throw err;
      } finally {
        this.isInitializing = false;
      }
    }
    return this.browser;
  }

  async resolve(rawUrl) {
    const startTime = Date.now();
    let context = null;
    let page = null;

    try {
      const browser = await this._getBrowser();

      // 构造类似真实 Android Chrome 移动端的上下文环境
      context = await browser.newContext({
        userAgent:
          "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        viewport: { width: 412, height: 915 },
        deviceScaleFactor: 2.625,
        isMobile: true,
        hasTouch: true,
        locale: "zh-CN",
        timezoneId: "Asia/Shanghai"
      });

      // 抹除 navigator.webdriver 特征
      await context.addInitScript(() => {
        Object.defineProperty(navigator, "webdriver", {
          get: () => undefined
        });
      });

      page = await context.newPage();

      let capturedAwemeData = null;
      let finalUrl = rawUrl;
      const redirectChain = [];

      // 核心：监听页面网络请求与响应，拦截自身发起的有效作品数据
      page.on("response", async (response) => {
        try {
          const respUrl = response.url();
          const contentType = response.headers()["content-type"] || "";

          // 匹配可能包含作品数据的异步接口（aweme/detail, iteminfo 等）
          if (
            (respUrl.includes("/aweme/detail") ||
              respUrl.includes("/iteminfo") ||
              respUrl.includes("/web/api/v2/aweme") ||
              respUrl.includes("share/video")) &&
            contentType.includes("application/json")
          ) {
            const body = await response.json();
            if (body && (body.aweme_detail || (body.item_list && body.item_list[0]))) {
              capturedAwemeData = body.aweme_detail || body.item_list[0];
              SafeLogger.info(this.name, "通过网络拦截成功捕获作品数据包", {
                apiUrl: respUrl,
                status: response.status(),
                contentLength: response.headers()["content-length"]
              });
            }
          }
        } catch (_err) {
          // 忽略流式或非 JSON 解析异常
        }
      });

      page.on("framenavigated", (frame) => {
        if (frame === page.mainFrame()) {
          const u = frame.url();
          finalUrl = u;
          redirectChain.push(u);
        }
      });

      SafeLogger.info(this.name, "开始访问抖音链接", { rawUrl });

      // 导航至抖音链接，最多等待 18 秒
      try {
        await page.goto(rawUrl, {
          waitUntil: "domcontentloaded",
          timeout: 18000
        });
      } catch (navErr) {
        SafeLogger.warn(this.name, "页面导航超时或异常，尝试从已加载内容中分析", {
          error: navErr.message,
          finalUrl
        });
      }

      // 等待 1.5 秒让异步数据包接收完
      await page.waitForTimeout(1500);

      const pageTitle = await page.title();
      const pageHtml = await page.content();
      const durationMs = Date.now() - startTime;

      SafeLogger.info(this.name, "页面基础加载完成", {
        finalUrl,
        redirectChain,
        pageTitle,
        durationMs,
        htmlSnippet: pageHtml.slice(0, 200)
      });

      // 检查是否遇到验证码/风控拦截页
      if (
        pageHtml.includes("verify") ||
        pageHtml.includes("captcha") ||
        pageHtml.includes("验证码") ||
        pageHtml.includes("secsdk")
      ) {
        // 如果虽然有风控提示但同时拦截到了有效作品，优先放行
        if (!capturedAwemeData) {
          SafeLogger.warn(this.name, "检测到页面需要安全验证", { finalUrl });
          return createErrorResult({
            error_code: ErrorCode.NEED_USER_INTERACTION,
            message: "抖音要求网页环境验证，请稍后重试"
          });
        }
      }

      // 如果通过网络拦截捕获到了作品数据
      if (capturedAwemeData) {
        return this._mapAwemeToResult(capturedAwemeData, durationMs);
      }

      // 如果未拦截到独立接口，尝试从当前页面 DOM / 挂载的 window 对象直接读取
      const domData = await page.evaluate(() => {
        try {
          const ssr = window._SSR_DATA || window._ROUTER_DATA || window.__INIT_PROPS__;
          if (ssr) return ssr;

          const renderDataEl = document.getElementById("RENDER_DATA");
          if (renderDataEl && renderDataEl.textContent) {
            return JSON.parse(decodeURIComponent(renderDataEl.textContent));
          }

          // 尝试查找页面内的 video 标签
          const videoEl = document.querySelector("video");
          if (videoEl && videoEl.src) {
            return {
              direct_video_src: videoEl.src,
              page_title: document.title
            };
          }
        } catch (_e) {
        }
        return null;
      });

      if (domData) {
        if (domData.direct_video_src) {
          return createSuccessResult({
            id: this._extractWorkId(finalUrl) || "direct_" + Date.now(),
            title: domData.page_title || pageTitle || "抖音短视频",
            author: "抖音创作者",
            cover_url: "",
            media: [
              {
                type: "video",
                url: domData.direct_video_src.replace("playwm", "play"),
                mime: "video/mp4"
              }
            ]
          });
        }

        const awemeFromDom = this._findAwemeInObject(domData);
        if (awemeFromDom) {
          return this._mapAwemeToResult(awemeFromDom, durationMs);
        }
      }

      // 若页面正常返回 HTML 但没有作品数据
      SafeLogger.warn(this.name, "页面返回了 HTML，但未能提取到作品数据结构", {
        finalUrl,
        pageTitle,
        htmlSnippet: pageHtml.slice(0, 200)
      });

      return createErrorResult({
        error_code: ErrorCode.HTML_NOT_DATA,
        message: "网页未返回有效作品数据 (HTML_NOT_DATA)"
      });
    } catch (err) {
      SafeLogger.error(this.name, "Browser 解析过程抛出异常", {
        error: err.message,
        durationMs: Date.now() - startTime
      });
      return createErrorResult({
        error_code: ErrorCode.RESOLVER_ERROR,
        message: `Browser 解析异常: ${err.message}`
      });
    } finally {
      if (page) await page.close().catch(() => {});
      if (context) await context.close().catch(() => {});
    }
  }

  _mapAwemeToResult(aweme, durationMs) {
    const id = aweme.aweme_id || aweme.id || String(Date.now());
    const title = aweme.desc || aweme.title || `抖音作品_${id}`;
    const author = aweme.author ? (aweme.author.nickname || aweme.author.name || "抖音创作者") : "抖音创作者";

    let coverUrl = "";
    if (aweme.video && aweme.video.cover && aweme.video.cover.url_list && aweme.video.cover.url_list[0]) {
      coverUrl = aweme.video.cover.url_list[0];
    } else if (aweme.video && aweme.video.origin_cover && aweme.video.origin_cover.url_list) {
      coverUrl = aweme.video.origin_cover.url_list[0];
    }

    const mediaList = [];

    // 视频地址提取
    if (aweme.video && aweme.video.play_addr && aweme.video.play_addr.url_list) {
      const rawUrls = aweme.video.play_addr.url_list;
      if (rawUrls.length > 0) {
        // playwm 替换为纯净无水印 play
        const cleanVideoUrl = rawUrls[0].replace("playwm", "play");
        mediaList.push({
          type: "video",
          url: cleanVideoUrl,
          mime: "video/mp4",
          expires_at: null
        });
      }
    }

    // 图集提取 (如有)
    if (mediaList.length === 0 && Array.isArray(aweme.images) && aweme.images.length > 0) {
      for (const img of aweme.images) {
        if (img.url_list && img.url_list[0]) {
          mediaList.push({
            type: "image",
            url: img.url_list[0],
            mime: "image/jpeg",
            expires_at: null
          });
        }
      }
      if (!coverUrl && mediaList.length > 0) {
        coverUrl = mediaList[0].url;
      }
    }

    if (mediaList.length === 0) {
      return createErrorResult({
        error_code: ErrorCode.MEDIA_URL_NOT_FOUND,
        message: "作品数据中未能获取到视频/图片媒体播放地址"
      });
    }

    SafeLogger.info(this.name, "成功提取作品元数据与媒体地址", {
      id,
      title,
      author,
      mediaCount: mediaList.length,
      durationMs
    });

    return createSuccessResult({
      id,
      title,
      author,
      cover_url: coverUrl,
      media: mediaList
    });
  }

  _findAwemeInObject(obj) {
    if (!obj || typeof obj !== "object") return null;
    if (obj.aweme_detail && obj.aweme_detail.video) return obj.aweme_detail;
    if (obj.video && (obj.desc || obj.aweme_id)) return obj;

    for (const key of Object.keys(obj)) {
      const val = obj[key];
      const found = this._findAwemeInObject(val);
      if (found) return found;
    }
    return null;
  }

  _extractWorkId(url) {
    if (!url) return null;
    const match = url.match(/(?:video|note)\/([0-9]{18,20})/);
    return match ? match[1] : null;
  }
}

module.exports = DouyinBrowserResolver;
