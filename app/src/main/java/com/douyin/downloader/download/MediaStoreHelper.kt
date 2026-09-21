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
    fun sanitizeFileName(rawTitle: String, workId: String, extension: String = "mp4"): String {
        val safeTitle = rawTitle.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").take(40)
        val ext = extension.trimStart('.')
        return "douyin_${workId}_${safeTitle}.$ext"
    }

    /**
     * 打开用于写入媒体（视频或图片）的 OutputStream 及返回生成的 Uri
     */
    fun createMediaOutputStream(
        context: Context,
        fileName: String,
        mimeType: String = "video/mp4",
        isImage: Boolean = false
    ): Pair<Uri?, OutputStream?> {
        val resolver: ContentResolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                if (isImage) {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER_NAME")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                } else {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER_NAME")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collectionUri = if (isImage) {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collectionUri, contentValues)
            val outputStream = uri?.let { resolver.openOutputStream(it) }
            return Pair(uri, outputStream)
        } else {
            // Android 9 及以下方案
            @Suppress("DEPRECATION")
            val baseDir = if (isImage) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            }
            val targetDir = File(baseDir, FOLDER_NAME)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = File(targetDir, fileName)
            val uri = Uri.fromFile(targetFile)
            val outputStream = FileOutputStream(targetFile)
            return Pair(uri, outputStream)
        }
    }

    fun createVideoOutputStream(
        context: Context,
        fileName: String
    ): Pair<Uri?, OutputStream?> = createMediaOutputStream(context, fileName, "video/mp4", false)

    /**
     * 下载写入完成，解除 IS_PENDING 状态并通知媒体扫描器
     */
    fun finishPendingMedia(context: Context, uri: Uri?, mimeType: String = "video/mp4") {
        if (uri == null) return
        val resolver: ContentResolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                if (mimeType.startsWith("image/")) {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                } else {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
            }
            resolver.update(uri, contentValues, null, null)
        } else {
            // 兼容旧版本扫描媒体库
            uri.path?.let { filePath ->
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    arrayOf(mimeType),
                    null
                )
            }
        }
    }

    fun finishPendingVideo(context: Context, uri: Uri?) = finishPendingMedia(context, uri, "video/mp4")

    /**
     * 下载失败时清理未写完的废弃文件
     */
    fun discardPendingVideo(context: Context, uri: Uri?) {
        if (uri == null) return
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }
}
