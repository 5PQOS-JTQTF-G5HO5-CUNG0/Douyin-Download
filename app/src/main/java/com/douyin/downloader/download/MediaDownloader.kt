package com.douyin.downloader.download

import android.content.Context
import com.douyin.downloader.data.model.MediaItem

/**
 * 媒体下载器统一接口 (与 Resolver 彻底解耦)
 */
interface MediaDownloader {
    /**
     * 触发下载指定媒体资源
     * @param context Android 上下文
     * @param media 媒体项 (视频/图片/音频)
     * @param title 作品标题
     * @param id 作品 ID
     */
    fun download(context: Context, media: MediaItem, title: String, id: String)

    /**
     * 取消当前进行中的下载任务
     */
    fun cancel()
}
