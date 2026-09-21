package com.douyin.downloader.domain.resolver

import com.douyin.downloader.data.model.ErrorCode
import com.douyin.downloader.data.model.MediaItem
import com.douyin.downloader.data.model.ResolveResponse
import com.douyin.downloader.data.model.ResolveStage
import com.douyin.downloader.util.UrlExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 客户端内置脱机 Mock 解析器，保证真机在无服务端部署时也能跑通输入到相册保存闭环
 */
class MockResolver : VideoResolver {

    companion object {
        const val SAMPLE_VIDEO_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        const val SAMPLE_COVER_URL =
            "https://images.unsplash.com/photo-1579202673506-ca3ce28943ef?w=800&auto=format&fit=crop&q=80"
    }

    override suspend fun resolve(
        url: String,
        onStageChanged: (ResolveStage) -> Unit
    ): ResolveResponse = withContext(Dispatchers.IO) {
        onStageChanged(ResolveStage.PARSING_URL)
        delay(200)

        val extractedUrl = UrlExtractor.extractFirstUrl(url)
            ?: return@withContext ResolveResponse(
                ok = false,
                errorCode = ErrorCode.INVALID_URL,
                message = "未能识别出有效链接"
            )

        onStageChanged(ResolveStage.OPENING_BROWSER)
        delay(300)

        onStageChanged(ResolveStage.FETCHING_METADATA)
        delay(300)

        val lower = extractedUrl.lowercase()

        // 模拟异常状态测试分支
        if (lower.contains("verify") || lower.contains("captcha")) {
            return@withContext ResolveResponse(
                ok = false,
                errorCode = ErrorCode.NEED_USER_INTERACTION,
                message = "抖音要求网页环境验证，请稍后重试"
            )
        }

        if (lower.contains("html")) {
            return@withContext ResolveResponse(
                ok = false,
                errorCode = ErrorCode.HTML_NOT_DATA,
                message = "网页未返回有效作品数据 (HTML_NOT_DATA)"
            )
        }

        if (lower.contains("fail") || lower.contains("notfound")) {
            return@withContext ResolveResponse(
                ok = false,
                errorCode = ErrorCode.EMPTY_DATA,
                message = "未找到作品数据或作品已删除"
            )
        }

        onStageChanged(ResolveStage.EXTRACTING_MEDIA)
        delay(200)

        val id = "7388" + Math.floor(100000000000000 + Math.random() * 900000000000000).toLong()
        ResolveResponse(
            ok = true,
            platform = "douyin",
            id = id,
            title = "【Mock 演示】Android 原生全流程测试视频",
            author = "极客评测",
            coverUrl = SAMPLE_COVER_URL,
            media = listOf(
                MediaItem(
                    type = "video",
                    url = SAMPLE_VIDEO_URL,
                    mime = "video/mp4",
                    expiresAt = null
                )
            )
        )
    }
}
