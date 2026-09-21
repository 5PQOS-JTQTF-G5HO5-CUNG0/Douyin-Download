package com.douyin.downloader.data.model

enum class ResolverErrorCode(val messageCn: String) {
    INVALID_URL("不是有效的抖音分享链接，请核对后重试"),
    SHORT_URL_FAILED("短链接重定向解析失败，请检查网络"),
    WORK_NOT_FOUND("作品不存在、已删除或为私密视频"),
    PLATFORM_CHANGED("平台结构已变更，解析引擎需适配更新"),
    MEDIA_EXPIRED("媒体资源地址已过期，请重新解析"),
    DOWNLOAD_FAILED("媒体下载失败，请稍后重试"),
    RATE_LIMITED("访问频率过快，已被平台限流，请稍后重试"),
    NETWORK_ERROR("网络连接异常，无法连通解析服务")
}

class ResolverException(
    val code: ResolverErrorCode,
    override val message: String = code.messageCn,
    cause: Throwable? = null
) : Exception(message, cause)
