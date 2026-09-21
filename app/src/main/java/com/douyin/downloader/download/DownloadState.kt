package com.douyin.downloader.download

sealed class DownloadState {
    object Idle : DownloadState()
    object Connecting : DownloadState()
    data class Downloading(
        val progress: Float, // 0.0f - 1.0f
        val currentBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : DownloadState()
    object Saving : DownloadState()
    data class Completed(
        val uriString: String,
        val fileName: String
    ) : DownloadState()
    data class Failed(
        val message: String
    ) : DownloadState()
}
