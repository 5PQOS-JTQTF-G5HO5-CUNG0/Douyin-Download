package com.douyin.downloader.util

import java.util.regex.Pattern

object UrlExtractor {

    private val URL_PATTERN = Pattern.compile(
        "(https?://[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * 从包含文案、表情的杂乱字符串中提取出首个合法的 HTTP/HTTPS 链接
     */
    fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val matcher = URL_PATTERN.matcher(text)
        if (matcher.find()) {
            var url = matcher.group(1) ?: return null
            // 清理常见的尾随中文标点或括号
            url = url.trimEnd('。', '，', '！', '、', '？', ')', '）', '】', '>', '」', '"', '\'')
            return url
        }
        return null
    }

    /**
     * 判断是否为疑似抖音相关链接（短链接域名 v.douyin.com 或 主站 douyin.com）
     */
    fun isDouyinUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("douyin.com") || lower.contains("iesdouyin.com")
    }
}
