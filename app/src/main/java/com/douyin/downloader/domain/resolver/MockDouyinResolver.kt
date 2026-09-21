package com.douyin.downloader.domain.resolver

import com.douyin.downloader.data.model.AuthorInfo
import com.douyin.downloader.data.model.MediaType
import com.douyin.downloader.data.model.ResolveResult
import com.douyin.downloader.data.model.ResolverErrorCode
import com.douyin.downloader.data.model.ResolverException
import com.douyin.downloader.data.model.UnifiedMedia
import com.douyin.downloader.util.UrlExtractor
import kotlinx.coroutines.delay

class MockDouyinResolver : DouyinResolver {

    companion object {
        // 公网可靠的测试视频流（真实 MP4，支持分块下载并可在手机相册直接硬解播放）
        const val SAMPLE_VIDEO_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        const val SAMPLE_COVER_URL = "https://images.unsplash.com/photo-1579202673506-ca3ce28943ef?w=800&auto=format&fit=crop&q=80"
        const val SAMPLE_AVATAR_URL = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80"
    }

    override fun canResolve(url: String): Boolean {
        // 在阶段一 Mock 模式下，只要能提取到 URL 均予以支持
        return UrlExtractor.extractFirstUrl(url) != null
    }

    override suspend fun resolve(rawUrl: String): Result<ResolveResult> {
        val extractedUrl = UrlExtractor.extractFirstUrl(rawUrl)
            ?: return Result.failure(
                ResolverException(
                    ResolverErrorCode.INVALID_URL,
                    "未能识别出有效链接，请粘贴完整抖音分享内容"
                )
            )

        // 模拟网络请求耗时 600ms
        delay(600)

        val lowerUrl = extractedUrl.lowercase()

        // 智能 Mock 异常分支
        return when {
            lowerUrl.contains("notfound") || lowerUrl.contains("fail") -> {
                Result.failure(ResolverException(ResolverErrorCode.WORK_NOT_FOUND))
            }
            lowerUrl.contains("expired") -> {
                Result.failure(ResolverException(ResolverErrorCode.MEDIA_EXPIRED))
            }
            lowerUrl.contains("ratelimit") -> {
                Result.failure(ResolverException(ResolverErrorCode.RATE_LIMITED))
            }
            lowerUrl.contains("timeout") -> {
                Result.failure(ResolverException(ResolverErrorCode.NETWORK_ERROR, "网络请求超时，请检查您的网络连接"))
            }
            lowerUrl.contains("invalid") -> {
                Result.failure(ResolverException(ResolverErrorCode.INVALID_URL))
            }
            else -> {
                // 默认正常模拟解析结果
                val workId = (7200000000000000000L + (Math.random() * 100000000).toLong()).toString()
                Result.success(
                    ResolveResult(
                        ok = true,
                        platform = "douyin",
                        id = workId,
                        title = "【Mock 演示】这是使用 Jetpack Compose 构建的原生抖音视频下载器测试视频",
                        author = AuthorInfo(
                            id = "mock_author_888",
                            name = "科技探索者",
                            avatarUrl = SAMPLE_AVATAR_URL
                        ),
                        coverUrl = SAMPLE_COVER_URL,
                        media = listOf(
                            UnifiedMedia(
                                type = MediaType.VIDEO,
                                url = SAMPLE_VIDEO_URL,
                                mime = "video/mp4",
                                width = 1080,
                                height = 1920,
                                sizeBytes = 15_600_000L // 约 15MB
                            )
                        ),
                        durationSeconds = 15,
                        expiresAt = System.currentTimeMillis() + 3600_000L
                    )
                )
            }
        }
    }
}
