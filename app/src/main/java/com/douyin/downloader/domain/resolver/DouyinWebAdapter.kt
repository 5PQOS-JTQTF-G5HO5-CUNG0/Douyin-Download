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
import java.net.URLDecoder
import java.util.regex.Pattern

/**
 * 针对抖音移动端 Web 分享页的解析适配器 (通过 SSR / Router Data 提取作品元数据)
 */
class DouyinWebAdapter(private val okHttpClient: OkHttpClient) {

    companion object {
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // 正则提取各类 SSR 内嵌 JSON
        private val RENDER_DATA_PATTERN = Pattern.compile(
            "<script id=\"RENDER_DATA\"[^>]*>([\\s\\S]*?)</script>",
            Pattern.CASE_INSENSITIVE
        )
        private val ROUTER_DATA_PATTERN = Pattern.compile(
            "window\\._ROUTER_DATA\\s*=\\s*([\\s\\S]*?);</script>",
            Pattern.CASE_INSENSITIVE
        )
        private val SSR_DATA_PATTERN = Pattern.compile(
            "window\\._SSR_DATA\\s*=\\s*([\\s\\S]*?);</script>",
            Pattern.CASE_INSENSITIVE
        )
    }

    suspend fun resolveByWebPage(itemId: String): Result<ResolveResult> {
        val shareUrl = "https://www.iesdouyin.com/share/video/$itemId"

        val request = Request.Builder()
            .url(shareUrl)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            return Result.failure(
                ResolverException(ResolverErrorCode.NETWORK_ERROR, "请求分享页网络异常", e)
            )
        }

        val statusCode = response.code
        val responseBody = response.body?.string() ?: ""

        if (statusCode == 403 || statusCode == 429) {
            return Result.failure(
                ResolverException(
                    ResolverErrorCode.RATE_LIMITED,
                    "访问被抖音安全风控拦截 (HTTP $statusCode)，请稍后重试"
                )
            )
        }

        if (!response.isSuccessful || responseBody.isBlank()) {
            return Result.failure(
                ResolverException(
                    ResolverErrorCode.WORK_NOT_FOUND,
                    "分享页请求失败 (HTTP $statusCode)，视频可能已被删除"
                )
            )
        }

        // 尝试从不同的 SSR 数据注入点提取 JSON
        val extractedJson = extractJsonFromHtml(responseBody)
        if (extractedJson != null) {
            val parseResult = parseAwemeFromJson(extractedJson, itemId)
            if (parseResult.isSuccess) {
                return parseResult
            }
        }

        // 若没有直接匹配到完整 JSON，尝试匹配内嵌播放流或备用字段
        val directMediaUrl = extractDirectMediaUrl(responseBody)
        if (directMediaUrl != null) {
            val title = extractTitleFromHtml(responseBody) ?: "抖音视频_$itemId"
            return Result.success(
                ResolveResult(
                    ok = true,
                    platform = "douyin",
                    id = itemId,
                    title = title,
                    author = AuthorInfo(id = "", name = "抖音作者", avatarUrl = ""),
                    coverUrl = "",
                    media = listOf(
                        UnifiedMedia(
                            type = MediaType.VIDEO,
                            url = directMediaUrl,
                            mime = "video/mp4"
                        )
                    )
                )
            )
        }

        val snippet = responseBody.take(120).replace("\n", " ")
        return Result.failure(
            ResolverException(
                ResolverErrorCode.PLATFORM_CHANGED,
                "未能在分享页中解析出视频数据 (HTTP $statusCode, 响应特征: $snippet)"
            )
        )
    }

    private fun extractJsonFromHtml(html: String): JSONObject? {
        // 1. 尝试 RENDER_DATA
        val renderMatcher = RENDER_DATA_PATTERN.matcher(html)
        if (renderMatcher.find()) {
            val raw = renderMatcher.group(1)?.trim() ?: ""
            val decoded = try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (_: Exception) {
                raw
            }
            try {
                return JSONObject(decoded)
            } catch (_: Exception) {
            }
        }

        // 2. 尝试 _ROUTER_DATA
        val routerMatcher = ROUTER_DATA_PATTERN.matcher(html)
        if (routerMatcher.find()) {
            val raw = routerMatcher.group(1)?.trim() ?: ""
            try {
                return JSONObject(raw)
            } catch (_: Exception) {
            }
        }

        // 3. 尝试 _SSR_DATA
        val ssrMatcher = SSR_DATA_PATTERN.matcher(html)
        if (ssrMatcher.find()) {
            val raw = ssrMatcher.group(1)?.trim() ?: ""
            try {
                return JSONObject(raw)
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun parseAwemeFromJson(root: JSONObject, itemId: String): Result<ResolveResult> {
        try {
            // 递归在 JSON 树中寻找包含 video 或 play_addr 的 aweme 对象
            val aweme = findAwemeObject(root)
                ?: return Result.failure(
                    ResolverException(
                        ResolverErrorCode.WORK_NOT_FOUND,
                        "作品数据结构中未找到视频详细信息"
                    )
                )

            val title = aweme.optString("desc", "").ifBlank { "抖音视频_$itemId" }

            // 作者信息
            val authorObj = aweme.optJSONObject("author")
            val authorName = authorObj?.optString("nickname", "抖音作者") ?: "抖音作者"
            val authorAvatar = authorObj?.optJSONObject("avatar_thumb")
                ?.optJSONArray("url_list")
                ?.optString(0, "") ?: ""

            // 封面图
            val videoObj = aweme.optJSONObject("video")
            val coverUrl = videoObj?.optJSONObject("cover")
                ?.optJSONArray("url_list")
                ?.optString(0, "")
                ?: videoObj?.optJSONObject("origin_cover")?.optJSONArray("url_list")?.optString(0, "")
                ?: ""

            // 播放直链
            val playAddr = videoObj?.optJSONObject("play_addr")
            val playList = playAddr?.optJSONArray("url_list")

            var rawPlayUrl = ""
            if (playList != null && playList.length() > 0) {
                rawPlayUrl = playList.optString(0, "")
            }

            if (rawPlayUrl.isBlank()) {
                // 尝试 note 图集
                val images = aweme.optJSONArray("images")
                if (images != null && images.length() > 0) {
                    val firstImg = images.optJSONObject(0)
                    val imgUrl = firstImg?.optJSONObject("url_list")?.optString(0, "") ?: ""
                    return Result.success(
                        ResolveResult(
                            ok = true,
                            platform = "douyin",
                            id = itemId,
                            title = title,
                            author = AuthorInfo(id = "", name = authorName, avatarUrl = authorAvatar),
                            coverUrl = imgUrl,
                            media = listOf(
                                UnifiedMedia(
                                    type = MediaType.IMAGE,
                                    url = imgUrl,
                                    mime = "image/jpeg"
                                )
                            )
                        )
                    )
                }

                return Result.failure(
                    ResolverException(
                        ResolverErrorCode.WORK_NOT_FOUND,
                        "作品未包含有效媒体播放地址"
                    )
                )
            }

            // 无水印处理：将 playwm 替换为 play
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
                    "解析作品 JSON 发生结构异常: ${e.message}",
                    e
                )
            )
        }
    }

    private fun findAwemeObject(node: Any?): JSONObject? {
        if (node is JSONObject) {
            if (node.has("aweme_detail")) {
                val detail = node.optJSONObject("aweme_detail")
                if (detail != null && detail.has("video")) return detail
            }
            if (node.has("video") && (node.has("desc") || node.has("aweme_id"))) {
                return node
            }
            val keys = node.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val found = findAwemeObject(node.opt(key))
                if (found != null) return found
            }
        }
        return null
    }

    private fun extractDirectMediaUrl(html: String): String? {
        val pattern = Pattern.compile("(https?://aweme\\.snssdk\\.com/aweme/v1/play/[^\"'\\s<>]+)")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)?.replace("playwm", "play")
        }
        return null
    }

    private fun extractTitleFromHtml(html: String): String? {
        val titlePattern = Pattern.compile("<title>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE)
        val matcher = titlePattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)?.replace("- 抖音", "")?.trim()
        }
        return null
    }
}
