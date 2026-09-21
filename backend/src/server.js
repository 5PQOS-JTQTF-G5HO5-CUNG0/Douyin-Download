const express = require("express");
const cors = require("cors");
const http = require("http");
const https = require("https");
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

// 流式媒体下载代理端点（彻底规避抖音 CDN 防盗链 HTTP 403）
app.get("/v1/proxy", (req, res) => {
  const targetUrl = req.query.url;
  if (!targetUrl) {
    return res.status(400).send("Missing target url parameter");
  }

  try {
    const parsed = new URL(targetUrl);
    const client = parsed.protocol === "https:" ? https : http;

    const options = {
      headers: {
        "User-Agent":
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        Referer: "https://www.douyin.com/",
        Accept: "*/*"
      }
    };

    if (req.headers.range) {
      options.headers.Range = req.headers.range;
    }

    const proxyReq = client.get(targetUrl, options, (proxyRes) => {
      // 若遇到重定向，跟随重定向
      if (proxyRes.statusCode >= 300 && proxyRes.statusCode < 400 && proxyRes.headers.location) {
        let redirectUrl = proxyRes.headers.location;
        if (redirectUrl.startsWith("/")) {
          redirectUrl = `${parsed.protocol}//${parsed.host}${redirectUrl}`;
        }
        const host = req.get("host") || `localhost:${port}`;
        return res.redirect(`http://${host}/v1/proxy?url=${encodeURIComponent(redirectUrl)}`);
      }

      res.status(proxyRes.statusCode);
      for (const [key, val] of Object.entries(proxyRes.headers)) {
        if (key.toLowerCase() !== "content-security-policy") {
          res.setHeader(key, val);
        }
      }
      proxyRes.pipe(res);
    });

    proxyReq.on("error", (err) => {
      SafeLogger.error("ServerProxy", "流代理下载失败", { error: err.message });
      if (!res.headersSent) {
        res.status(502).send("Proxy error: " + err.message);
      }
    });
  } catch (err) {
    res.status(400).send("Invalid target URL");
  }
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

    // 如果成功且包含媒体，注入当前服务端的流式代理直链（保证客户端下载永远不被 403 拦截）
    if (result && result.ok && Array.isArray(result.media) && result.media.length > 0) {
      const host = req.get("host") || `localhost:${port}`;
      for (const item of result.media) {
        if (item.url && item.url.startsWith("http")) {
          // 保存原始直链，并将对外直链封装为代理直链
          item.raw_url = item.url;
          item.url = `http://${host}/v1/proxy?url=${encodeURIComponent(item.url)}`;
        }
      }
    }

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
  console.log(`🔀 流代理端点: GET  http://localhost:${port}/v1/proxy?url=...`);
  console.log(`💚 健康检查端点: GET  http://localhost:${port}/health`);
  console.log(`=======================================================`);
});
