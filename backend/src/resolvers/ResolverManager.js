const DouyinBrowserResolver = require("./DouyinBrowserResolver");
const LegacySharePageResolver = require("./LegacySharePageResolver");
const MockResolver = require("./MockResolver");
const { ErrorCode, createErrorResult } = require("../types/ResolveResult");
const SafeLogger = require("../logger/safeLogger");

class ResolverManager {
  constructor() {
    this.browserResolver = new DouyinBrowserResolver();
    this.legacyResolver = new LegacySharePageResolver();
    this.mockResolver = new MockResolver();
  }

  async resolve(url) {
    if (!url || typeof url !== "string" || !url.trim()) {
      return createErrorResult({
        error_code: ErrorCode.INVALID_URL,
        message: "链接不能为空，请输入有效的抖音分享链接"
      });
    }

    const cleanUrl = url.trim();

    // 1. 优先检查是否为 Mock 模式测试指令
    if (this.mockResolver.canResolve(cleanUrl)) {
      SafeLogger.info("ResolverManager", "进入 MockResolver 测试模式", { cleanUrl });
      return await this.mockResolver.resolve(cleanUrl);
    }

    SafeLogger.info("ResolverManager", "开始执行 BrowserResolver 主解析流程", { cleanUrl });

    // 2. 主解析器：真实无头浏览器环境
    try {
      const browserResult = await this.browserResolver.resolve(cleanUrl);
      if (browserResult.ok) {
        return browserResult;
      }

      SafeLogger.warn("ResolverManager", "BrowserResolver 解析未成功，准备降级至 LegacyResolver", {
        errorCode: browserResult.error_code,
        message: browserResult.message
      });

      // 如果遇到明确需要人工验证的拦截，直接返回，不再盲目走 Legacy
      if (browserResult.error_code === ErrorCode.NEED_USER_INTERACTION) {
        return browserResult;
      }
    } catch (browserErr) {
      SafeLogger.error("ResolverManager", "BrowserResolver 发生未捕获异常", {
        error: browserErr.message
      });
    }

    // 3. 备用解析器：Legacy HTTP 请求降级
    SafeLogger.info("ResolverManager", "启动 LegacySharePageResolver 备用流程", { cleanUrl });
    try {
      const legacyResult = await this.legacyResolver.resolve(cleanUrl);
      if (legacyResult.ok) {
        return legacyResult;
      }

      SafeLogger.warn("ResolverManager", "LegacyResolver 亦未成功", {
        errorCode: legacyResult.error_code,
        message: legacyResult.message
      });

      return legacyResult;
    } catch (legacyErr) {
      SafeLogger.error("ResolverManager", "Legacy 降级异常", { error: legacyErr.message });
      return createErrorResult({
        error_code: ErrorCode.RESOLVER_ERROR,
        message: `解析链路全部中断: ${legacyErr.message}`
      });
    }
  }
}

module.exports = ResolverManager;
