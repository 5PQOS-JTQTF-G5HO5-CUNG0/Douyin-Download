const { chromium } = require("playwright");
const VideoResolver = require("./VideoResolver");
const { ErrorCode, createSuccessResult, createErrorResult } = require("../types/ResolveResult");
const SafeLogger = require("../logger/safeLogger");

/**
 * 主解析器：基于真实无头浏览器环境（PC Web 端）监听并提取抖音作品
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
          await new Promise((r) => setTimeout(r, 100));
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
            "--disable-dev-shm-usage",
            "--disable-accelerated-2d-canvas",
            "--no-first-run",
            "--no-zygote",
            "--disable-gpu",
            "--disable-blink-features=AutomationControlled",
            "--lang=zh-CN,zh"
          ]
        });
        SafeLogger.info(this.name, "Chromium 启动完成");
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

      // 构造无痕 PC Chrome 环境
      context = await browser.newContext({
        userAgent:
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        viewport: { width: 1280, height: 720 },
        locale: "zh-CN",
        timezoneId: "Asia/Shanghai"
      });

      // 注入请求头伪装
      await context.setExtraHTTPHeaders({
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "sec-ch-ua": '"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"',
        "sec-ch-ua-mobile": "?0",
        "sec-ch-ua-platform": '"Windows"'
      });

      // 抹除自动化痕迹，伪装为真实 Win32 Chrome
      await context.addInitScript(() => {
        Object.defineProperty(navigator, "webdriver", { get: () => undefined });
        Object.defineProperty(navigator, "languages", { get: () => ["zh-CN", "zh", "en"] });
        Object.defineProperty(navigator, "platform", { get: () => "Win32" });
        window.chrome = { runtime: {} };
      });

      page = await context.newPage();

      let capturedAwemeData = null;
      let finalUrl = rawUrl;
      const redirectChain = [];

      // Promise 监听器：一旦截获到 aweme 数据，立即唤醒
      let onAwemeCaptured = null;
      const awemePromise = new Promise((resolve) => {
        onAwemeCaptured = resolve;
      });

      // 核心：监听页面网络请求与响应，拦截真实接口
      page.on("response", async (response) => {
        try {
          const respUrl = response.url();
          const contentType = response.headers()["content-type"] || "";

          // 匹配 PC 端核心接口 /aweme/v1/web/aweme/detail 或通用 aweme 详情
          if (
            (respUrl.includes("aweme/detail") ||
              respUrl.includes("aweme/v1") ||
              respUrl.includes("/iteminfo") ||
              respUrl.includes("/web/api/v2/aweme")) &&
            contentType.includes("application/json")
          ) {
            const body = await response.json();
            if (body && (body.aweme_detail || (body.item_list && body.item_list[0]))) {
              capturedAwemeData = body.aweme_detail || body.item_list[0];
              SafeLogger.info(this.name, "通过网络拦截成功捕获作品数据包", {
                apiUrl: respUrl.slice(0, 100),
                status: response.status()
              });
              if (onAwemeCaptured) onAwemeCaptured(capturedAwemeData);
            }
          }
        } catch (_err) {
          // 忽略非 JSON 或流错误
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

      // 第一阶段：导航至初始短链接，追踪 302 重定向
      try {
        await page.goto(rawUrl, {
          waitUntil: "domcontentloaded",
          timeout: 20000
        });
      } catch (navErr) {
        SafeLogger.warn(this.name, "首轮页面导航超时或异常", {
          error: navErr.message,
          finalUrl
        });
      }

      // 提取作品 ID（支持 video/xxx 或 note/xxx）
      let itemId = this._extractWorkId(finalUrl) || this._extractWorkId(rawUrl);

      // 如果尚未从 URL 提取到，尝试从 DOM/_ROUTER_DATA 提取
      if (!itemId) {
        try {
          itemId = await page.evaluate(() => {
            const r = window._ROUTER_DATA;
            if (r && r.loaderData) {
              for (const key of Object.keys(r.loaderData)) {
                const val = r.loaderData[key];
                if (val && val.itemId) return String(val.itemId);
              }
            }
            return null;
          });
        } catch (_) {}
      }

      // 检查是否重定向到了抖音首页（作品已被删除或设为私密）
      const isHomePage =
        /^https?:\/\/(www\.)?douyin\.com\/?(\?.*)?$/.test(finalUrl) ||
        finalUrl.includes("douyin.com/home");
      if (isHomePage && !itemId) {
        SafeLogger.warn(this.name, "短链接重定向至抖音首页，作品可能已下架或删除", { finalUrl, rawUrl });
        return createErrorResult({
          error_code: ErrorCode.EMPTY_DATA,
          message: "作品可能已删除、下架或设为私密 (EMPTY_DATA)"
        });
      }

      // 第二阶段：如果捕获到 itemId 且尚未抓到数据包，直接进入 PC Web 详情页触发 aweme/detail
      if (itemId && !capturedAwemeData) {
        const desktopDetailUrl = `https://www.douyin.com/video/${itemId}`;
        if (!finalUrl.includes(`/video/${itemId}`)) {
          SafeLogger.info(this.name, "检测到作品 ID，跳转至桌面端视频详情页触发数据包", {
            itemId,
            desktopDetailUrl
          });
          try {
            await page.goto(desktopDetailUrl, {
              waitUntil: "domcontentloaded",
              timeout: 20000
            });
          } catch (deskErr) {
            SafeLogger.warn(this.name, "桌面详情页导航提示", { error: deskErr.message });
          }
        }
      }

      // 等待网络包捕获（跨国 VPS 网络通信，放宽至 8 秒超时）
      if (!capturedAwemeData) {
        await Promise.race([
          awemePromise,
          new Promise((resolve) => setTimeout(resolve, 8000))
        ]);
      }

      const durationMs = Date.now() - startTime;
      const pageTitle = await page.title().catch(() => "");
      const pageHtml = await page.content().catch(() => "");

      // 检查是否遇到真实验证码/风控拦截页（如 verify.snssdk.com 或滑块弹窗）
      const isRealCaptcha =
        finalUrl.includes("verify.snssdk.com") ||
        pageHtml.includes("captcha-verify-image") ||
        pageHtml.includes("secsdk-captcha-drag-wrapper") ||
        pageHtml.includes("验证码中间页");

      if (isRealCaptcha && !capturedAwemeData) {
        SafeLogger.warn(this.name, "检测到页面需要安全滑块验证", { finalUrl });
        return createErrorResult({
          error_code: ErrorCode.NEED_USER_INTERACTION,
          message: "抖音要求网页环境验证，请稍后重试"
        });
      }

      // 1. 如果通过网络拦截成功抓取了 aweme 数据
      if (capturedAwemeData) {
        return this._mapAwemeToResult(capturedAwemeData, durationMs);
      }

      // 2. 如果网络拦截未中，尝试从 DOM / RENDER_DATA 提取
      const domData = await page.evaluate(() => {
        try {
          const renderDataEl = document.getElementById("RENDER_DATA");
          if (renderDataEl && renderDataEl.textContent) {
            return JSON.parse(decodeURIComponent(renderDataEl.textContent));
          }

          const videoEl = document.querySelector("video");
          if (videoEl && videoEl.src) {
            return {
              direct_video_src: videoEl.src,
              page_title: document.title
            };
          }
        } catch (_e) {}
        return null;
      });

      if (domData) {
        if (domData.direct_video_src) {
          return createSuccessResult({
            id: itemId || "direct_" + Date.now(),
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

      // 3. 兜底错误（打印丰富诊断）
      SafeLogger.warn(this.name, "页面已加载但未能提取到有效作品数据", {
        finalUrl,
        pageTitle,
        itemId,
        htmlLength: pageHtml.length,
        htmlSnippet: pageHtml.slice(0, 300)
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
    const author = aweme.author ? aweme.author.nickname || aweme.author.name || "抖音创作者" : "抖音创作者";

    let coverUrl = "";
    if (aweme.video && aweme.video.cover && aweme.video.cover.url_list && aweme.video.cover.url_list[0]) {
      coverUrl = aweme.video.cover.url_list[0];
    } else if (aweme.video && aweme.video.origin_cover && aweme.video.origin_cover.url_list) {
      coverUrl = aweme.video.origin_cover.url_list[0];
    }

    const mediaList = [];

    const isImageNote =
      aweme.aweme_type === 68 || (Array.isArray(aweme.images) && aweme.images.length > 0);

    if (isImageNote && Array.isArray(aweme.images) && aweme.images.length > 0) {
      // 1. 图文作品提取：提取全部高清无水印图片
      for (const img of aweme.images) {
        const candidates = [...(img.url_list || []), ...(img.download_url_list || [])];
        // 优先选取 jpeg 格式以保证相册与系统解码器最佳兼容性
        const bestUrl =
          candidates.find((u) => u.includes(".jpeg") || u.includes(".jpg")) ||
          candidates[0];

        if (bestUrl) {
          mediaList.push({
            type: "image",
            url: bestUrl,
            mime: "image/jpeg",
            expires_at: null
          });
        }
      }
      if (!coverUrl && mediaList.length > 0) {
        coverUrl = mediaList[0].url;
      }
    } else {
      // 2. 视频作品提取：优先提取兼容性最高的 H.264 MP4 视频流或最高码率流
      let videoUrl = "";

      if (aweme.video && Array.isArray(aweme.video.bit_rate) && aweme.video.bit_rate.length > 0) {
        // 优先筛选 is_h265 === 0 (标准 H.264 MP4)，兼容所有 Android/iOS 播放器
        const h264Item = aweme.video.bit_rate.find(
          (b) => b.is_h265 === 0 && b.play_addr && Array.isArray(b.play_addr.url_list) && b.play_addr.url_list.length > 0
        );
        const selected = h264Item || aweme.video.bit_rate.find(
          (b) => b.play_addr && Array.isArray(b.play_addr.url_list) && b.play_addr.url_list.length > 0
        );
        if (selected && selected.play_addr.url_list[0]) {
          videoUrl = selected.play_addr.url_list[0];
        }
      }

      if (
        !videoUrl &&
        aweme.video &&
        aweme.video.play_addr &&
        Array.isArray(aweme.video.play_addr.url_list) &&
        aweme.video.play_addr.url_list.length > 0
      ) {
        videoUrl = aweme.video.play_addr.url_list[0];
      }

      // 剔除音频直链（如 .mp3，防止 BGM 误入视频流）
      if (videoUrl && !videoUrl.includes(".mp3")) {
        const cleanVideoUrl = videoUrl.replace("playwm", "play");
        mediaList.push({
          type: "video",
          url: cleanVideoUrl,
          mime: "video/mp4",
          expires_at: null
        });
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
    const match = url.match(/(?:video|note)\/([0-9]{15,22})/);
    return match ? match[1] : null;
  }
}

module.exports = DouyinBrowserResolver;
