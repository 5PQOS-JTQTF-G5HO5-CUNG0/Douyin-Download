package com.douyin.downloader.domain.resolver

import com.douyin.downloader.data.model.ResolveResult
import com.douyin.downloader.data.model.ResolverErrorCode
import com.douyin.downloader.data.model.ResolverException
import com.douyin.downloader.util.UrlExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class RealDouyinResolver : DouyinResolver {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val WORK_ID_PATTERN = Pattern.compile("(?:video|note)/([0-9]{18,20})")
        private val MODAL_ID_PATTERN = Pattern.compile("modal_id=([0-9]{18,20})")
    }

    private val redirectClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false) // 手动捕获 Location 302 重定向
        .followSslRedirects(false)
        .build()

    private val apiClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val webAdapter = DouyinWebAdapter(apiClient)
    private val apiAdapter = DouyinApiAdapter(apiClient)
    private val mockFallback = MockDouyinResolver()

    override fun canResolve(url: String): Boolean {
        return UrlExtractor.extractFirstUrl(url) != null
    }

    override suspend fun resolve(rawUrl: String): Result<ResolveResult> = withContext(Dispatchers.IO) {
        val extractedUrl = UrlExtractor.extractFirstUrl(rawUrl)
            ?: return@withContext Result.failure(
                ResolverException(
                    ResolverErrorCode.INVALID_URL,
                    "未能识别到有效的抖音链接，请检查复制的内容"
                )
            )

        // 若输入特定测试指令（如 test/mock/expired/notfound），进入智能测试桩
        val lower = extractedUrl.lowercase()
        if (lower.contains("mock") || lower.contains("fail") ||
            lower.contains("notfound") || lower.contains("expired") ||
            lower.contains("ratelimit") || lower.contains("timeout")
        ) {
            return@withContext mockFallback.resolve(extractedUrl)
        }

        // 1. 获取重定向长链接
        val longUrl = try {
            resolveLongUrl(extractedUrl)
        } catch (e: Exception) {
            return@withContext Result.failure(
                ResolverException(
                    ResolverErrorCode.SHORT_URL_FAILED,
                    "解析短链接重定向失败，请检查网络后重试",
                    e
                )
            )
        }

        // 2. 提取作品 ID
        val workId = extractWorkId(longUrl) ?: extractWorkId(extractedUrl)
        if (workId.isNullOrBlank()) {
            return@withContext Result.failure(
                ResolverException(
                    ResolverErrorCode.INVALID_URL,
                    "无法从链接中识别出作品 ID (URL: $longUrl)"
                )
            )
        }

        // 3. 优先通过 Web 适配器解析
        val webResult = webAdapter.resolveByWebPage(workId)
        if (webResult.isSuccess) {
            return@withContext webResult
        }

        // 4. 若 Web 适配器失败，降级尝试 API 适配器
        val apiResult = apiAdapter.resolveByApi(workId)
        if (apiResult.isSuccess) {
            return@withContext apiResult
        }

        // 5. 组合两者的错误诊断返回给客户端
        val errorWeb = webResult.exceptionOrNull()
        val errorApi = apiResult.exceptionOrNull()
        val finalError = errorWeb ?: errorApi ?: ResolverException(ResolverErrorCode.PLATFORM_CHANGED)

        Result.failure(finalError)
    }

    /**
     * 追踪短链接重定向（如 v.douyin.com/xxx），获取包含作品 ID 的长链接
     */
    private fun resolveLongUrl(shortUrl: String): String {
        var currentUrl = shortUrl
        var redirectCount = 0
        val maxRedirects = 5

        while (redirectCount < maxRedirects) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = redirectClient.newCall(request).execute()
            val code = response.code

            if (code in 300..399) {
                val location = response.header("Location")
                if (!location.isNullOrBlank()) {
                    currentUrl = if (location.startsWith("http")) {
                        location
                    } else {
                        // 相对路径
                        val originalUri = okhttp3.HttpUrl.get(currentUrl)
                        originalUri.resolve(location)?.toString() ?: location
                    }
                    redirectCount++
                    continue
                }
            }
            break
        }

        return currentUrl
    }

    private fun extractWorkId(url: String): String? {
        val workMatcher = WORK_ID_PATTERN.matcher(url)
        if (workMatcher.find()) {
            return workMatcher.group(1)
        }
        val modalMatcher = MODAL_ID_PATTERN.matcher(url)
        if (modalMatcher.find()) {
            return modalMatcher.group(1)
        }
        return null
    }
}
