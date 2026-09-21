package com.douyin.downloader.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    INVALID_URL,
    NETWORK_ERROR,
    REDIRECT_FAILED,
    HTML_NOT_DATA,
    EMPTY_DATA,
    UNSUPPORTED_PAGE,
    RATE_LIMITED,
    ACCESS_DENIED,
    NEED_USER_INTERACTION,
    MEDIA_URL_NOT_FOUND,
    RESOLVER_ERROR
}

enum class ResolveStage(val text: String) {
    IDLE("准备就绪"),
    PARSING_URL("正在解析分享链接..."),
    OPENING_BROWSER("正在打开网页环境..."),
    FETCHING_METADATA("正在获取作品信息..."),
    EXTRACTING_MEDIA("正在获取视频地址...")
}

@Serializable
data class MediaItem(
    val type: String = "video", // "video" | "image"
    val url: String,
    val mime: String = "video/mp4",
    @SerialName("expires_at")
    val expiresAt: Long? = null
)

@Serializable
data class ResolveResponse(
    val ok: Boolean = true,
    val platform: String = "douyin",
    val id: String? = null,
    val title: String? = null,
    val author: String? = null,
    @SerialName("cover_url")
    val coverUrl: String? = null,
    val media: List<MediaItem> = emptyList(),
    @SerialName("error_code")
    val errorCode: ErrorCode? = null,
    val message: String? = null
) {
    /**
     * 将底层错误码转换为对终端用户友好的提示文案（不暴露 Argus, iteminfo 等内部细节）
     */
    fun getUserFriendlyMessage(): String {
        return when (errorCode) {
            ErrorCode.NEED_USER_INTERACTION, ErrorCode.HTML_NOT_DATA ->
                "抖音要求网页环境验证，请稍后重试"
            ErrorCode.EMPTY_DATA, ErrorCode.UNSUPPORTED_PAGE ->
                "未找到作品数据，视频可能已被删除或设为私密"
            ErrorCode.MEDIA_URL_NOT_FOUND, ErrorCode.REDIRECT_FAILED ->
                "视频地址已失效，请重新解析"
            ErrorCode.RATE_LIMITED ->
                "访问过于频繁，已被平台临时限流，请稍后重试"
            ErrorCode.ACCESS_DENIED ->
                "访问被平台拒绝，请稍后重试"
            ErrorCode.INVALID_URL ->
                "未能识别出有效的抖音链接，请检查复制的内容"
            ErrorCode.NETWORK_ERROR ->
                "网络连接异常，无法连通解析服务，请检查网络或后端地址"
            else -> message ?: "解析失败，请稍后重试"
        }
    }
}
