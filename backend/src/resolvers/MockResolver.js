const VideoResolver = require("./VideoResolver");
const { ErrorCode, createSuccessResult, createErrorResult } = require("../types/ResolveResult");

/**
 * 完整模拟解析器：供 Android 客户端在无公网或免抖音服务器环境下全流程自测
 */
class MockResolver extends VideoResolver {
  constructor() {
    super("MockResolver");
  }

  canResolve(url) {
    if (!url || typeof url !== "string") return false;
    const lower = url.toLowerCase();
    return (
      lower.includes("mock") ||
      lower.includes("test") ||
      lower.includes("fail") ||
      lower.includes("verify") ||
      lower.includes("expired")
    );
  }

  async resolve(url) {
    const lower = (url || "").toLowerCase();

    // 异常场景模拟
    if (lower.includes("verify") || lower.includes("captcha")) {
      return createErrorResult({
        error_code: ErrorCode.NEED_USER_INTERACTION,
        message: "抖音要求网页环境验证，请稍后重试"
      });
    }

    if (lower.includes("html")) {
      return createErrorResult({
        error_code: ErrorCode.HTML_NOT_DATA,
        message: "网页未返回有效作品数据 (HTML_NOT_DATA)"
      });
    }

    if (lower.includes("fail") || lower.includes("notfound")) {
      return createErrorResult({
        error_code: ErrorCode.EMPTY_DATA,
        message: "未找到作品数据或作品已删除"
      });
    }

    if (lower.includes("ratelimit")) {
      return createErrorResult({
        error_code: ErrorCode.RATE_LIMITED,
        message: "访问频率过快，已被平台限流"
      });
    }

    // 默认返回真实可下载落盘到 Android 相册的测试 MP4
    const workId = "7388" + Math.floor(100000000000000 + Math.random() * 900000000000000);
    return createSuccessResult({
      id: workId,
      title: "【Mock 演示】这是用于 Android 全流程测试的无水印短视频",
      author: "科技前沿测评",
      cover_url: "https://images.unsplash.com/photo-1579202673506-ca3ce28943ef?w=800&auto=format&fit=crop&q=80",
      media: [
        {
          type: "video",
          url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
          mime: "video/mp4",
          expires_at: null
        }
      ]
    });
  }
}

module.exports = MockResolver;
