package com.douyin.downloader.download

import android.content.Context
import com.douyin.downloader.data.model.MediaItem

/**
 * Android 原生前台服务与 MediaStore 下载器实现
 */
class AndroidMediaDownloader : MediaDownloader {

    override fun download(context: Context, media: MediaItem, title: String, id: String) {
        AppDownloadManager.startDownload(
            context = context,
            workId = id,
            title = title,
            videoUrl = media.url
        )
    }

    override fun cancel() {
        AppDownloadManager.cancelDownload()
    }
}
