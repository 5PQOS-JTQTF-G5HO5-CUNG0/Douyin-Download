package com.douyin.downloader.domain.resolver

import com.douyin.downloader.data.model.ResolveResponse
import com.douyin.downloader.data.model.ResolveStage

/**
 * 统一视频解析器接口
 */
interface VideoResolver {
    /**
     * 异步解析视频链接
     * @param url 待解析的链接或包含文案的内容
     * @param onStageChanged 状态阶段回调 (用于驱动 UI 展示阶段提示)
     */
    suspend fun resolve(
        url: String,
        onStageChanged: (ResolveStage) -> Unit = {}
    ): ResolveResponse
}
