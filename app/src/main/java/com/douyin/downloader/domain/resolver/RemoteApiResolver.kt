package com.douyin.downloader.domain.resolver

import com.douyin.downloader.data.model.ErrorCode
import com.douyin.downloader.data.model.ResolveResponse
import com.douyin.downloader.data.model.ResolveStage
import com.douyin.downloader.util.UrlExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RemoteApiResolver(
    private var baseUrl: String = com.douyin.downloader.data.local.AppPreferences.getServerUrl()
) : VideoResolver {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun updateBaseUrl(newUrl: String) {
        baseUrl = newUrl.trimEnd('/')
        com.douyin.downloader.data.local.AppPreferences.setServerUrl(baseUrl)
    }

    fun getBaseUrl(): String = baseUrl

    override suspend fun resolve(
        url: String,
        onStageChanged: (ResolveStage) -> Unit
    ): ResolveResponse = withContext(Dispatchers.IO) {
        onStageChanged(ResolveStage.PARSING_URL)

        val extractedUrl = UrlExtractor.extractFirstUrl(url)
            ?: return@withContext ResolveResponse(
                ok = false,
                errorCode = ErrorCode.INVALID_URL,
                message = "未能识别出有效的链接，请粘贴完整抖音分享内容"
            )

        delay(150)
        onStageChanged(ResolveStage.OPENING_BROWSER)

        val requestJson = JSONObject().apply {
            put("url", extractedUrl)
            put("client_version", "1.0.0")
        }.toString()

        val endpoint = "$baseUrl/v1/resolve"
        val requestBody = requestJson.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .build()

        delay(300)
        onStageChanged(ResolveStage.FETCHING_METADATA)

        try {
            val response = client.newCall(request).execute()
            onStageChanged(ResolveStage.EXTRACTING_MEDIA)

            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful || bodyString.isBlank()) {
                return@withContext ResolveResponse(
                    ok = false,
                    errorCode = ErrorCode.RESOLVER_ERROR,
                    message = "服务端返回异常状态 (HTTP ${response.code})"
                )
            }

            try {
                json.decodeFromString<ResolveResponse>(bodyString)
            } catch (jsonErr: Exception) {
                ResolveResponse(
                    ok = false,
                    errorCode = ErrorCode.HTML_NOT_DATA,
                    message = "解析服务端响应失败: ${jsonErr.message}"
                )
            }
        } catch (e: Exception) {
            ResolveResponse(
                ok = false,
                errorCode = ErrorCode.NETWORK_ERROR,
                message = "无法连接至后端服务 ($baseUrl): ${e.message}"
            )
        }
    }
}
