package com.douyin.downloader.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaType {
    VIDEO,
    IMAGE
}

@Serializable
data class UnifiedMedia(
    val type: MediaType = MediaType.VIDEO,
    val url: String,
    val mime: String = "video/mp4",
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L
)
