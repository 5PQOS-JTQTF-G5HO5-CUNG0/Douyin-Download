package com.douyin.downloader.download

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppDownloadManager {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var isCancelled = false

    fun updateState(state: DownloadState) {
        _downloadState.value = state
    }

    fun isDownloadCancelled(): Boolean = isCancelled

    fun cancelDownload() {
        isCancelled = true
        _downloadState.value = DownloadState.Idle
    }

    fun resetState() {
        isCancelled = false
        _downloadState.value = DownloadState.Idle
    }

    fun startDownload(
        context: Context,
        workId: String,
        title: String,
        videoUrl: String
    ) {
        resetState()
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_WORK_ID, workId)
            putExtra(DownloadService.EXTRA_TITLE, title)
            putExtra(DownloadService.EXTRA_VIDEO_URL, videoUrl)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
