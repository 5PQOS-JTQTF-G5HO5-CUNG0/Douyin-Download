package com.douyin.downloader.download

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object MediaStoreHelper {

    private const val FOLDER_NAME = "Douyin"

    /**
     * 清理并安全化文件名，去除 Windows/Linux 路径中的非法字符
     */
    fun sanitizeFileName(rawTitle: String, workId: String): String {
        val safeTitle = rawTitle.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").take(40)
        return "douyin_${workId}_${safeTitle}.mp4"
    }

    /**
     * 打开用于写入视频的 OutputStream 及返回生成的 Uri
     */
    fun createVideoOutputStream(
        context: Context,
        fileName: String
    ): Pair<Uri?, OutputStream?> {
        val resolver: ContentResolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER_NAME")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            val outputStream = uri?.let { resolver.openOutputStream(it) }
            return Pair(uri, outputStream)
        } else {
            // Android 9 及以下方案
            @Suppress("DEPRECATION")
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                FOLDER_NAME
            )
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }
            val targetFile = File(moviesDir, fileName)
            val uri = Uri.fromFile(targetFile)
            val outputStream = FileOutputStream(targetFile)
            return Pair(uri, outputStream)
        }
    }

    /**
     * 下载写入完成，解除 IS_PENDING 状态并通知媒体扫描器
     */
    fun finishPendingVideo(context: Context, uri: Uri?) {
        if (uri == null) return
        val resolver: ContentResolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            resolver.update(uri, contentValues, null, null)
        } else {
            // 兼容旧版本扫描媒体库
            uri.path?.let { filePath ->
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    arrayOf("video/mp4"),
                    null
                )
            }
        }
    }

    /**
     * 下载失败时清理未写完的废弃文件
     */
    fun discardPendingVideo(context: Context, uri: Uri?) {
        if (uri == null) return
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
        }
    }
}
