/**
 * 抖音视频解析统一数据契约与错误枚举
 */
const ErrorCode = Object.freeze({
  INVALID_URL: "INVALID_URL",
  NETWORK_ERROR: "NETWORK_ERROR",
  REDIRECT_FAILED: "REDIRECT_FAILED",
  HTML_NOT_DATA: "HTML_NOT_DATA",
  EMPTY_DATA: "EMPTY_DATA",
  UNSUPPORTED_PAGE: "UNSUPPORTED_PAGE",
  RATE_LIMITED: "RATE_LIMITED",
  ACCESS_DENIED: "ACCESS_DENIED",
  NEED_USER_INTERACTION: "NEED_USER_INTERACTION",
  MEDIA_URL_NOT_FOUND: "MEDIA_URL_NOT_FOUND",
  RESOLVER_ERROR: "RESOLVER_ERROR"
});

function createSuccessResult({
  platform = "douyin",
  id,
  title,
  author,
  cover_url,
  media = []
}) {
  return {
    ok: true,
    platform,
    id,
    title,
    author,
    cover_url,
    media: media.map(m => ({
      type: m.type || "video",
      url: m.url,
      mime: m.mime || "video/mp4",
      expires_at: m.expires_at || null
    })),
    error_code: null,
    message: null
  };
}

function createErrorResult({
  platform = "douyin",
  error_code = ErrorCode.RESOLVER_ERROR,
  message = "解析失败"
}) {
  return {
    ok: false,
    platform,
    id: null,
    title: null,
    author: null,
    cover_url: null,
    media: [],
    error_code,
    message
  };
}

module.exports = {
  ErrorCode,
  createSuccessResult,
  createErrorResult
};
