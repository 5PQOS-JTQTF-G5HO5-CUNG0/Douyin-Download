package com.douyin.downloader.download

import android.content.Context
import com.douyin.downloader.data.model.MediaItem

/**
 * Android 原生前台服务与 MediaStore 下载器实现
 */
class AndroidMediaDownloader : MediaDownloader {

    override fun download(context: Context, media: MediaItem, title: String, id: String) {
        val mediaType = media.type.ifEmpty { "video" }
        val mimeType = media.mime ?: if (mediaType == "image") "image/jpeg" else "video/mp4"
        AppDownloadManager.startDownload(
            context = context,
            workId = id,
            title = title,
            videoUrl = media.url,
            mediaType = mediaType,
            mimeType = mimeType
        )
    }

    override fun cancel() {
        AppDownloadManager.cancelDownload()
    }
}
