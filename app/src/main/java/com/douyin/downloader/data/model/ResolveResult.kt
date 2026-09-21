package com.douyin.downloader.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ResolveResult(
    val ok: Boolean = true,
    val platform: String = "douyin",
    val id: String,
    val title: String,
    val author: AuthorInfo,
    val coverUrl: String,
    val media: List<UnifiedMedia>,
    val durationSeconds: Long = 0L,
    val expiresAt: Long = 0L
)
