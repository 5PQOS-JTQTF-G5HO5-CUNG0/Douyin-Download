package com.douyin.downloader.domain.resolver

import com.douyin.downloader.data.model.ResolveResult

interface DouyinResolver {
    /**
     * 判断当前解析器是否支持处理此 URL
     */
    fun canResolve(url: String): Boolean

    /**
     * 异步解析给定的抖音分享文本或链接
     * @return Result<ResolveResult> 成功返回元数据，失败抛出 ResolverException
     */
    suspend fun resolve(rawUrl: String): Result<ResolveResult>
}
