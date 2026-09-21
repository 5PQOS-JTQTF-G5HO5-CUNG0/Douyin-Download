package com.douyin.downloader.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthorInfo(
    val id: String = "",
    val name: String = "未知作者",
    val avatarUrl: String = ""
)
