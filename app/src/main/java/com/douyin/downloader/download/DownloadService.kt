package com.douyin.downloader.download

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.douyin.downloader.DouyinApp
import com.douyin.downloader.MainActivity
import com.douyin.downloader.util.ShareHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class DownloadService : Service() {

    companion object {
        const val ACTION_START_DOWNLOAD = "com.douyin.downloader.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.douyin.downloader.CANCEL_DOWNLOAD"

        const val EXTRA_WORK_ID = "extra_work_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_MEDIA_TYPE = "extra_media_type"
        const val EXTRA_MIME_TYPE = "extra_mime_type"

        private const val NOTIFICATION_ID = 1001
        private const val SUCCESS_NOTIFICATION_ID = 1002
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var currentDownloadJob: Job? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val workId = intent.getStringExtra(EXTRA_WORK_ID) ?: ""
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "未知作品"
                val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
                val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "video"
                val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: if (mediaType == "image") "image/jpeg" else "video/mp4"
                if (videoUrl.isNotEmpty()) {
                    startForegroundNotification(title, mediaType)
                    startDownloading(workId, title, videoUrl, mediaType, mimeType)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelCurrentDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification(title: String, mediaType: String) {
        val notification = buildProgressNotification(title, 0, false, mediaType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildProgressNotification(
        title: String,
        progress: Int,
        indeterminate: Boolean,
        mediaType: String = "video"
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val pendingCancel = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeText = if (mediaType == "image") "图片" else "视频"
        return NotificationCompat.Builder(this, DouyinApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("正在下载$typeText")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingOpenApp)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", pendingCancel)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startDownloading(
        workId: String,
        title: String,
        videoUrl: String,
        mediaType: String = "video",
        mimeType: String = "video/mp4"
    ) {
        currentDownloadJob?.cancel()
        currentDownloadJob = serviceScope.launch {
            AppDownloadManager.updateState(DownloadState.Connecting)

            var targetUri: Uri? = null
            var outputStream: OutputStream? = null
            var inputStream: InputStream? = null

            try {
                val isImage = mediaType == "image"
                val ext = if (isImage) "jpg" else "mp4"
                val fileName = MediaStoreHelper.sanitizeFileName(title, workId, ext)
                val pair = MediaStoreHelper.createMediaOutputStream(applicationContext, fileName, mimeType, isImage)
                targetUri = pair.first
                outputStream = pair.second

                if (targetUri == null || outputStream == null) {
                    throw IllegalStateException("无法创建媒体库写入流，请检查存储权限")
                }

                val request = Request.Builder()
                    .url(videoUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Referer", "https://www.douyin.com/")
                    .header("Accept", "*/*")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("服务器响应错误: HTTP ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("服务器返回了空内容")
                val totalLength = body.contentLength()
                inputStream = body.byteStream()

                val buffer = ByteArray(8192)
                var bytesCopied: Long = 0
                var readCount: Int
                var lastNotifyTime = System.currentTimeMillis()
                var bytesSinceLastNotify = 0L

                while (inputStream.read(buffer).also { readCount = it } != -1) {
                    if (AppDownloadManager.isDownloadCancelled()) {
                        throw InterruptedException("用户主动取消下载")
                    }

                    outputStream.write(buffer, 0, readCount)
                    bytesCopied += readCount
                    bytesSinceLastNotify += readCount

                    val now = System.currentTimeMillis()
                    val timeDelta = now - lastNotifyTime

                    // 每 200ms 更新一次进度与速率
                    if (timeDelta >= 200) {
                        val speedBps = (bytesSinceLastNotify * 1000) / timeDelta
                        val progress = if (totalLength > 0) {
                            (bytesCopied.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        AppDownloadManager.updateState(
                            DownloadState.Downloading(
                                progress = progress,
                                currentBytes = bytesCopied,
                                totalBytes = totalLength,
                                speedBytesPerSec = speedBps
                            )
                        )

                        // 更新通知栏
                        val notification = buildProgressNotification(
                            title,
                            (progress * 100).toInt(),
                            totalLength <= 0,
                            mediaType
                        )
                        NotificationManagerCompat.from(this@DownloadService)
                            .notify(NOTIFICATION_ID, notification)

                        lastNotifyTime = now
                        bytesSinceLastNotify = 0L
                    }
                }

                outputStream.flush()
                AppDownloadManager.updateState(DownloadState.Saving)

                // 完成 MediaStore 写入
                MediaStoreHelper.finishPendingMedia(applicationContext, targetUri, mimeType)

                AppDownloadManager.updateState(
                    DownloadState.Completed(
                        uriString = targetUri.toString(),
                        fileName = fileName
                    )
                )
                showSuccessNotification(title, mediaType, targetUri.toString())

            } catch (e: Exception) {
                if (e is InterruptedException || AppDownloadManager.isDownloadCancelled()) {
                    MediaStoreHelper.discardPendingVideo(applicationContext, targetUri)
                    AppDownloadManager.updateState(DownloadState.Idle)
                } else {
                    MediaStoreHelper.discardPendingVideo(applicationContext, targetUri)
                    AppDownloadManager.updateState(
                        DownloadState.Failed(e.message ?: "下载过程中发生未知异常")
                    )
                }
            } finally {
                try {
                    inputStream?.close()
                    outputStream?.close()
                } catch (ignored: Exception) {
                }
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private fun showSuccessNotification(title: String, mediaType: String = "video", uriString: String? = null) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeText = if (mediaType == "image") "图片" else "视频"
        val notificationBuilder = NotificationCompat.Builder(this, DouyinApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("${typeText}下载完成，已保存到相册")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingOpenApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (uriString != null) {
            val isImage = mediaType == "image"
            val shareIntent = ShareHelper.createShareChooserIntent(this, uriString, isImage, title)
            val pendingShare = PendingIntent.getActivity(
                this,
                2,
                shareIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(android.R.drawable.ic_menu_share, "分享", pendingShare)
        }

        NotificationManagerCompat.from(this).notify(SUCCESS_NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun cancelCurrentDownload() {
        AppDownloadManager.cancelDownload()
        currentDownloadJob?.cancel()
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentDownloadJob?.cancel()
    }
}
