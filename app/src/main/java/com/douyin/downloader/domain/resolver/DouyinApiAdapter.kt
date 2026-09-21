package com.douyin.downloader.domain.resolver

import com.douyin.downloader.data.model.AuthorInfo
import com.douyin.downloader.data.model.MediaType
import com.douyin.downloader.data.model.ResolveResult
import com.douyin.downloader.data.model.ResolverErrorCode
import com.douyin.downloader.data.model.ResolverException
import com.douyin.downloader.data.model.UnifiedMedia
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 针对抖音官方公开 API 接口的解析适配器 (iteminfo 备用通道)
 */
class DouyinApiAdapter(private val okHttpClient: OkHttpClient) {

    companion object {
        private const val API_URL = "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids="
        private const val USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"
    }

    suspend fun resolveByApi(itemId: String): Result<ResolveResult> {
        val request = Request.Builder()
            .url(API_URL + itemId)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            return Result.failure(
                ResolverException(ResolverErrorCode.NETWORK_ERROR, "API 接口请求异常", e)
            )
        }

        if (!response.isSuccessful) {
            return Result.failure(
                ResolverException(
                    ResolverErrorCode.WORK_NOT_FOUND,
                    "API 接口返回错误 (HTTP ${response.code})"
                )
            )
        }

        val jsonStr = response.body?.string() ?: ""
        if (jsonStr.isBlank()) {
            return Result.failure(
                ResolverException(ResolverErrorCode.PLATFORM_CHANGED, "API 接口返回空数据")
            )
        }

        try {
            val root = JSONObject(jsonStr)
            val itemList = root.optJSONArray("item_list")
            if (itemList == null || itemList.length() == 0) {
                return Result.failure(
                    ResolverException(
                        ResolverErrorCode.WORK_NOT_FOUND,
                        "作品已被删除或设为私密内容"
                    )
                )
            }

            val item = itemList.getJSONObject(0)
            val title = item.optString("desc", "").ifBlank { "抖音视频_$itemId" }

            val authorObj = item.optJSONObject("author")
            val authorName = authorObj?.optString("nickname", "抖音作者") ?: "抖音作者"
            val authorAvatar = authorObj?.optJSONObject("avatar_thumb")
                ?.optJSONArray("url_list")
                ?.optString(0, "") ?: ""

            val videoObj = item.optJSONObject("video")
            val coverUrl = videoObj?.optJSONObject("cover")
                ?.optJSONArray("url_list")
                ?.optString(0, "") ?: ""

            val playAddr = videoObj?.optJSONObject("play_addr")
            val playList = playAddr?.optJSONArray("url_list")

            var rawPlayUrl = ""
            if (playList != null && playList.length() > 0) {
                rawPlayUrl = playList.optString(0, "")
            }

            if (rawPlayUrl.isBlank()) {
                return Result.failure(
                    ResolverException(ResolverErrorCode.WORK_NOT_FOUND, "未获取到有效播放地址")
                )
            }

            // 无水印处理
            val noWatermarkUrl = rawPlayUrl.replace("playwm", "play")

            return Result.success(
                ResolveResult(
                    ok = true,
                    platform = "douyin",
                    id = itemId,
                    title = title,
                    author = AuthorInfo(
                        id = authorObj?.optString("uid", "") ?: "",
                        name = authorName,
                        avatarUrl = authorAvatar
                    ),
                    coverUrl = coverUrl,
                    media = listOf(
                        UnifiedMedia(
                            type = MediaType.VIDEO,
                            url = noWatermarkUrl,
                            mime = "video/mp4"
                        )
                    ),
                    durationSeconds = videoObj.optLong("duration", 0L) / 1000L
                )
            )
        } catch (e: Exception) {
            return Result.failure(
                ResolverException(
                    ResolverErrorCode.PLATFORM_CHANGED,
                    "解析 API 返回的 JSON 失败: ${e.message}",
                    e
                )
            )
        }
    }
}
