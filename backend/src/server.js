const express = require("express");
const cors = require("cors");
const ResolverManager = require("./resolvers/ResolverManager");
const SafeLogger = require("./logger/safeLogger");
const { ErrorCode, createErrorResult } = require("./types/ResolveResult");

const app = express();
const port = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

const resolverManager = new ResolverManager();

// 健康检查端点
app.get("/health", (req, res) => {
  res.json({
    status: "healthy",
    time: new Date().toISOString(),
    service: "douyin-resolver-backend"
  });
});

// 核心解析端点
app.post("/v1/resolve", async (req, res) => {
  const { url, client_version } = req.body || {};

  SafeLogger.info("Server", "收到解析请求", {
    url,
    clientVersion: client_version || "unknown",
    ip: req.ip
  });

  if (!url) {
    return res.status(200).json(
      createErrorResult({
        error_code: ErrorCode.INVALID_URL,
        message: "缺少必需的 url 参数"
      })
    );
  }

  try {
    const result = await resolverManager.resolve(url);
    return res.status(200).json(result);
  } catch (err) {
    SafeLogger.error("Server", "处理解析请求时捕获未处理异常", { error: err.message });
    return res.status(200).json(
      createErrorResult({
        error_code: ErrorCode.RESOLVER_ERROR,
        message: `服务端内部错误: ${err.message}`
      })
    );
  }
});

app.listen(port, "0.0.0.0", () => {
  console.log(`=======================================================`);
  console.log(`🚀 Douyin Video Resolver Backend 已在端口 ${port} 启动`);
  console.log(`📡 解析 API 契约: POST http://localhost:${port}/v1/resolve`);
  console.log(`💚 健康检查端点: GET  http://localhost:${port}/health`);
  console.log(`=======================================================`);
});
