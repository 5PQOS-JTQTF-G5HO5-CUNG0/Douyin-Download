package com.douyin.downloader.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {

    /**
     * 将媒体路径或 URI 解析为外部应用可安全读取的 Content URI
     */
    fun getShareableUri(context: Context, uriString: String): Uri {
        val parsedUri = Uri.parse(uriString)
        return when (parsedUri.scheme) {
            "content" -> parsedUri
            "file" -> {
                val file = File(parsedUri.path ?: "")
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            else -> {
                val file = File(uriString)
                if (file.exists()) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    parsedUri
                }
            }
        }
    }

    /**
     * 构建发送给微信或其他社交 App 的 ACTION_SEND 选择器 Intent
     */
    fun createShareChooserIntent(
        context: Context,
        uriString: String,
        isImage: Boolean,
        title: String? = null
    ): Intent {
        val shareUri = getShareableUri(context, uriString)
        val mimeType = if (isImage) "image/*" else "video/*"

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            // 绑定 ClipData 确保 Android 10+ 下目标应用（如微信）获得持久的 URI 读取授权
            clipData = ClipData.newRawUri(title ?: "Douyin Media", shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(sendIntent, if (isImage) "分享图片至" else "分享视频至").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 打开系统分享底栏（支持微信、QQ、系统其他分享目标）
     */
    fun shareMedia(
        context: Context,
        uriString: String,
        isImage: Boolean,
        title: String? = null
    ) {
        try {
            val chooser = createShareChooserIntent(context, uriString, isImage, title)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "启动分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
