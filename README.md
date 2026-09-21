# 抖音短视频下载助手 (Douyin Downloader)

现代化原生 Android 应用（Kotlin + Jetpack Compose + Material 3），实现了从抖音分享链接解析、多状态展示、前台服务流式下载、断点续传，到安全落盘至 Android 系统相册（`Movies/Douyin`）的完整闭环。

---

## 阶段一特性与全流程说明 (MVP Mock)

本阶段已完成客户端全部核心基础与链路验证：
- **可插拔 Resolver 架构**：通过 `DouyinResolver` 统一接口实现业务与解析解耦，阶段一内置 `MockDouyinResolver`，脱机纯原生运行；阶段二可一键热插拔切换为真实抖音网页/API 解析或独立服务端 API。
- **系统级分享接入 (ACTION_SEND)**：在抖音 App 内点击「分享」→ 选择「抖音下载助手」→ 自动调起应用并开始提取解析。
- **纯手动剪贴板交互**：支持手动粘贴，不进行隐蔽式后台剪贴板窥探，尊重用户隐私。
- **OkHttp + Foreground Service 前台下载器**：支持大文件分块流式写入、实时下载速率计算、通知栏进度条显示及随时取消机制。
- **Android 10~14+ Scoped Storage 规范**：使用 `MediaStore.Video.Media` 配合 `IS_PENDING` 机制，下载完成后自动入库系统相册，并在相册中立即可见播放。

---

## 智能 Mock 测试指令表

在输入框中输入不同的内容可触发对应的业务链路与异常场景：

| 输入内容规则 | 模拟结果 | 验证目标 |
| :--- | :--- | :--- |
| 任意常规链接 (如 `https://v.douyin.com/abc/` 或整段分享文案) | **解析成功** | 返回 15MB 真实短视频流，点击下载后真机写入 `Movies/Douyin`，相册可直接点开播放 |
| 包含 `fail` 或 `notfound` | **WORK_NOT_FOUND** | 模拟视频已下架或作者设为私密视频，UI 展示友好警示 |
| 包含 `expired` | **MEDIA_EXPIRED** | 模拟 CDN 媒体地址时效过期，验证客户端重试解析提示 |
| 包含 `ratelimit` | **RATE_LIMITED** | 模拟触发平台风控与限流 |
| 包含 `timeout` | **NETWORK_ERROR** | 模拟网络超时及断网状态 |
| 包含 `invalid` | **INVALID_URL** | 模拟非法或无法识别的链接 |

---

## 项目目录结构

```
DouyinDownloader/
├── .github/workflows/
│   └── build-apk.yml               # GitHub Actions 自动化编译打包 CI
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml # 声明权限、ACTION_SEND Intent 过滤器与前台服务
│   │   │   ├── java/com/douyin/downloader/
│   │   │   │   ├── data/model/     # 强类型数据模型与统一错误码 (ResolveResult, AuthorInfo 等)
│   │   │   │   ├── domain/resolver/# DouyinResolver 接口与 MockDouyinResolver 实现
│   │   │   │   ├── download/       # OkHttp 前台服务与 MediaStore IS_PENDING 落盘引擎
│   │   │   │   ├── ui/             # Jetpack Compose UI (HomeScreen, ResultCard, 现代主题)
│   │   │   │   ├── util/           # UrlExtractor 文本与短链正则清洗工具
│   │   │   │   ├── DouyinApp.kt    # 全局 Application (初始化 NotificationChannel)
│   │   │   │   └── MainActivity.kt # 应用程序入口与分享接收分发
│   │   │   └── res/                # 颜色、矢量图、主题与多语言字符串
│   │   └── test/                   # URL 提取与 Mock 状态机单元测试
│   └── build.gradle.kts            # App 模块编译脚本
├── gradle/wrapper/                 # Gradle Wrapper 运行时
├── build.gradle.kts                # 根工程配置
└── settings.gradle.kts             # 模块拓扑定义
```

---

## 如何构建与生成 APK

### 方式一：GitHub Actions 自动构建（推荐，免配置本地 SDK）
1. 将当前工程推送到您的 GitHub 仓库：
   ```bash
   git init
   git add .
   git commit -m "feat: complete MVP with mock resolver and download pipeline"
   git remote add origin <your-repo-url>
   git push -u origin main
   ```
2. 打开 GitHub 仓库页面，点击顶部 **Actions** 标签。
3. 找到 **Build Android APK** 工作流，点击 **Run workflow**（或 push 代码时会自动触发）。
4. 构建完成后，在下方 **Artifacts** 处直接点击下载 `DouyinDownloader-debug-apk.zip`，解压即可得到 `app-debug.apk`，安装到真机即可使用！

### 方式二：本地命令行编译（需本地安装 JDK 17 与 Android SDK）
在项目根目录下执行：
```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
产出物路径位于：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 阶段二已完成（真实抖音解析引擎已接入）

当前应用已默认启用 `RealDouyinResolver`：
1. **短链自动还原**：支持输入 `v.douyin.com` 短链接，自动跟随 302 重定向获取作品长链接；
2. **多适配器架构**：
   - 优先通过 `DouyinWebAdapter` 获取移动端 SSR 页面并提取无水印视频直链（`playwm` 自动替换为 `play`）；
   - 降级支持 `DouyinApiAdapter` 官方 API 接口备用兜底；
3. **安全与脱敏**：不硬编码任何私人 Cookie 或签名密匙，出现风控拦截时给出友好提示；
4. **测试桩保留**：输入包含 `mock`、`fail`、`notfound`、`expired`、`timeout` 等保留字时自动走模拟测试桩，方便随时脱机验证 UI 容错。
