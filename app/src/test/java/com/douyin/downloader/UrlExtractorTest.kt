package com.douyin.downloader

import com.douyin.downloader.util.UrlExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun testExtractShortUrl() {
        val input = "7.32 02/10 复制打开抖音，看看【小猫咪的日常】的作品： https://v.douyin.com/iJabc12/ 复制此链接打开！"
        val extracted = UrlExtractor.extractFirstUrl(input)
        assertEquals("https://v.douyin.com/iJabc12/", extracted)
        assertTrue(UrlExtractor.isDouyinUrl(extracted))
    }

    @Test
    fun testExtractUrlWithChinesePunctuation() {
        val input = "看这个视频吧：https://v.douyin.com/abc123/，非常好看"
        val extracted = UrlExtractor.extractFirstUrl(input)
        assertEquals("https://v.douyin.com/abc123/", extracted)
    }

    @Test
    fun testExtractEmptyOrInvalid() {
        assertNull(UrlExtractor.extractFirstUrl(""))
        assertNull(UrlExtractor.extractFirstUrl("这是纯文本没有任何链接"))
    }

    @Test
    fun testIsDouyinUrl() {
        assertTrue(UrlExtractor.isDouyinUrl("https://v.douyin.com/iJabc12/"))
        assertTrue(UrlExtractor.isDouyinUrl("https://www.douyin.com/video/7234567890123456789"))
        assertFalse(UrlExtractor.isDouyinUrl("https://www.bilibili.com/video/BV1xx"))
    }
}
