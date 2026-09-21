# 抖音视频下载助手 (Douyin Downloader) — Resolver Adapter 架构

本工程基于 **Resolver Adapter 架构** 实现了抖音短视频/图集的高可用无水印解析、多阶段状态通知、前台服务流式下载与 Android 原生 `MediaStore`（`Movies/Douyin`）相册安全落盘。

彻底摒弃了在客户端直接爬取旧 HTML（容易被字节跳动 Argus 风控盾返回纯 JS 壳代码导致 `HTML_NOT_DATA`）的脆弱方案，转由后端真实 Chromium 浏览器环境监听拦截网络数据包提取媒体直链，并对 Android 客户端提供统一契约接口与解耦下载器。

---

## 一、系统整体架构

```
[抖音分享 / 剪贴板输入]
       │
       ▼
 [Android 客户端] (Kotlin + Jetpack Compose)
       │  • 提取短链/长链 URL
       │  • POST /v1/resolve
       ▼
 [Backend 解析服务] (Node.js + Playwright Chromium)
       │
       ▼ [ResolverManager 调度链]
 ┌─────────────────────────────────────────────────────────┐
 │ 1. MockResolver (开发/离线测试，真机脱机自测下载与相册)  │
 │ 2. DouyinBrowserResolver (主解析：真实无头浏览器环境)  │
 │    └─ 监听拦截页面自身网络请求 (aweme/detail 等)         │
 │ 3. LegacySharePageResolver (仅作备用降级 Fallback)      │
 └─────────────────────────────────────────────────────────┘
       │ (返回统一契约 JSON)
       ▼
 [Android 客户端展示]
       │  • 解析阶段展示 (正在打开网页环境... / 正在获取视频地址...)
       │  • 友好错误屏蔽 (不暴露 Argus, iteminfo 等内部细节)
       │  • 预览卡片 (封面, 标题, 作者, 规格)
       ▼
 [MediaDownloader 接口] (下载引擎彻底解耦)
       │
       ▼
 [AndroidMediaDownloader] (OkHttp 前台服务 + MediaStore Movies/Douyin)
```

---

## 二、统一返回契约 (API Contract)

### 端点：`POST /v1/resolve`

#### 请求体 (JSON)
```json
{
  "url": "https://v.douyin.com/xxxxxx/",
  "client_version": "1.0.0"
}
```

#### 成功响应 (HTTP 200)
```json
{
  "ok": true,
  "platform": "douyin",
  "id": "7388123456789012345",
  "title": "作品标题文案",
  "author": "创作者昵称",
  "cover_url": "https://p3-sign.douyinpic.com/...",
  "media": [
    {
      "type": "video",
      "url": "https://aweme.snssdk.com/aweme/v1/play/?video_id=...",
      "mime": "video/mp4",
      "expires_at": null
    }
  ],
  "error_code": null,
  "message": null
}
```

#### 失败响应 (HTTP 200)
```json
{
  "ok": false,
  "platform": "douyin",
  "id": null,
  "title": null,
  "author": null,
  "cover_url": null,
  "media": [],
  "error_code": "NEED_USER_INTERACTION",
  "message": "抖音要求网页环境验证，请稍后重试"
}
```

---

## 三、结构化错误分类表

| 错误码 (error_code) | 含义解释 | 客户端对普通用户呈现 |
| :--- | :--- | :--- |
| `INVALID_URL` | 输入内容不是合法的 URL | 请检查复制的内容是否包含有效链接 |
| `NETWORK_ERROR` | 网络无法连通或超时 | 网络连接异常，无法连通解析服务 |
| `REDIRECT_FAILED` | 短链重定向失败 | 短链接解析失败，请稍后重试 |
| `HTML_NOT_DATA` | 页面返回 200 HTML 但缺少作品数据 (Argus 拦截) | 抖音要求网页环境验证，请稍后重试 |
| `EMPTY_DATA` | 接口无数据返回 | 未找到作品数据，视频可能已被删除或设为私密 |
| `UNSUPPORTED_PAGE` | 不受支持的页面类型 | 该页面暂不支持解析 |
| `RATE_LIMITED` | 触发平台频率限制 (HTTP 429) | 访问过于频繁，已被平台临时限流 |
| `ACCESS_DENIED` | 访问被拒绝 (HTTP 403) | 访问被平台拒绝，请稍后重试 |
| `NEED_USER_INTERACTION` | 遇到滑动验证码/人机校验 | 抖音要求网页环境验证，请稍后重试 |
| `MEDIA_URL_NOT_FOUND` | 成功抓取作品元数据但直链提取失败 | 视频地址已失效，请重新解析 |
| `RESOLVER_ERROR` | 解析链路内部未捕获异常 | 解析异常，请稍后重试 |

---

## 四、安全与脱敏日志规范

本系统严格遵守安全边界：
- **禁止客户端模拟私有签名**：不把 `x-secsdk-web-signature` 或私有逆向算法打包进 APK。
- **禁止硬编码或泄露 Cookie**：服务端不硬编码私人 Cookie，不把会话凭证返回给客户端，日志彻底过滤 `Cookie`, `Authorization`, `Token` 等字段。
- **脱敏诊断日志**：仅记录 HTTP 状态码、Content-Type、响应体积、耗时、前 200 字符 HTML 摘要。

---

## 五、启动与运行指南

### 1. 启动 Backend 解析服务
在电脑或服务器上进入 `backend` 目录：

```bash
cd backend

# 安装依赖
npm install

# 首次运行需安装 Chromium 浏览器内核 (仅需执行一次)
npx playwright install chromium

# 运行自动化测试集
npm test

# 启动服务端 (默认监听 0.0.0.0:3000)
npm start
```
控制台将输出：
```text
🚀 Douyin Video Resolver Backend 已在端口 3000 启动
📡 解析 API 契约: POST http://localhost:3000/v1/resolve
💚 健康检查端点: GET  http://localhost:3000/health
```

---

### 2. Android 客户端真机测试与出包

#### 方式 A：GitHub Actions 云端自动出包（推荐）
推送到 GitHub 仓库后，GitHub Actions 会自动编译并产出可安装的 `app-debug.apk`。

#### 方式 B：本地命令行编译
```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
产出物位于：`app/build/outputs/apk/debug/app-debug.apk`。

#### 真机端两种测试模式：
1. **脱机免后端测试 (Mock 模式)**：  
   打开 App → 点击右上角「⚙ 设置」→ 打开「内置离线 Mock 模式」开关。此时输入任意测试链接均可脱机完整跑通：**解析阶段动画 → 预览卡片 → 点击下载 → 前台流式下载 → 保存至系统相册 (Movies/Douyin)**。
2. **连接局域网真实后端模式**：  
   将手机与电脑连入同一 Wi-Fi，在 App「⚙ 设置」中输入电脑局域网 IP（例如 `http://192.168.1.100:3000`），即可进行真实抖音链接的解析与下载。

---

## 六、未来抖音改版维护指南（关键）

> 💡 **核心解耦优势**：
> 如果未来抖音再次修改网页结构、反爬机制或内部接口，**请直接修改以下文件，绝不要修改 Android UI 或客户端代码**：
> 
> 👉 **`backend/src/resolvers/DouyinBrowserResolver.js`**
> 
> - 如果接口地址变更：修改该文件中 `page.on("response")` 的 URL 匹配规则；
> - 如果播放流字段变更：修改 `_mapAwemeToResult()` 中的提取字段；
> - **Android 客户端 UI 与下载器永久无需重新发版或重新安装！**
