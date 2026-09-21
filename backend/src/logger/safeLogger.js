/**
 * 安全脱敏日志模块
 * 严禁输出 Cookie、Authorization、完整 Token、完整请求头
 */
class SafeLogger {
  static info(tag, message, meta = {}) {
    this._log("INFO", tag, message, meta);
  }

  static warn(tag, message, meta = {}) {
    this._log("WARN", tag, message, meta);
  }

  static error(tag, message, meta = {}) {
    this._log("ERROR", tag, message, meta);
  }

  static _log(level, tag, message, meta = {}) {
    const sanitizedMeta = this._sanitize(meta);
    const timestamp = new Date().toISOString();
    console.log(JSON.stringify({
      timestamp,
      level,
      tag,
      message,
      ...sanitizedMeta
    }));
  }

  static _sanitize(obj) {
    if (!obj || typeof obj !== "object") return {};
    const sanitized = {};

    for (const [key, value] of Object.entries(obj)) {
      const lowerKey = key.toLowerCase();
      // 敏感字段彻底过滤
      if (
        lowerKey.includes("cookie") ||
        lowerKey.includes("auth") ||
        lowerKey.includes("token") ||
        lowerKey.includes("session") ||
        lowerKey.includes("headers")
      ) {
        continue;
      }

      // HTML/长文本仅截取前 200 字符
      if (typeof value === "string" && value.length > 200) {
        sanitized[key] = value.slice(0, 200) + `... [truncated, total ${value.length} chars]`;
      } else {
        sanitized[key] = value;
      }
    }

    return sanitized;
  }
}

module.exports = SafeLogger;
