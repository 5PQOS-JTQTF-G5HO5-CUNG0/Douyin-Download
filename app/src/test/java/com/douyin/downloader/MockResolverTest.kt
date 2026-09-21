package com.douyin.downloader

import com.douyin.downloader.data.model.ErrorCode
import com.douyin.downloader.data.model.ResolveStage
import com.douyin.downloader.domain.resolver.MockResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockResolverTest {

    private val resolver = MockResolver()

    @Test
    fun testMockSuccessScenario() = runTest {
        val stages = mutableListOf<ResolveStage>()
        val result = resolver.resolve("https://v.douyin.com/mock_success/") { stage ->
            stages.add(stage)
        }

        assertTrue(result.ok)
        assertEquals("douyin", result.platform)
        assertNotNull(result.id)
        assertNotNull(result.title)
        assertEquals(1, result.media.size)
        assertEquals("video", result.media[0].type)
        assertTrue(result.media[0].url.startsWith("http"))
        assertTrue(stages.contains(ResolveStage.OPENING_BROWSER))
        assertTrue(stages.contains(ResolveStage.FETCHING_METADATA))
    }

    @Test
    fun testNeedUserInteractionScenario() = runTest {
        val result = resolver.resolve("https://v.douyin.com/verify_test/")
        assertFalse(result.ok)
        assertEquals(ErrorCode.NEED_USER_INTERACTION, result.errorCode)
        assertEquals("抖音要求网页环境验证，请稍后重试", result.getUserFriendlyMessage())
    }

    @Test
    fun testHtmlNotDataScenario() = runTest {
        val result = resolver.resolve("https://v.douyin.com/html_page_test/")
        assertFalse(result.ok)
        assertEquals(ErrorCode.HTML_NOT_DATA, result.errorCode)
        assertEquals("抖音要求网页环境验证，请稍后重试", result.getUserFriendlyMessage())
    }

    @Test
    fun testEmptyDataScenario() = runTest {
        val result = resolver.resolve("https://v.douyin.com/notfound_video/")
        assertFalse(result.ok)
        assertEquals(ErrorCode.EMPTY_DATA, result.errorCode)
        assertEquals("未找到作品数据，视频可能已被删除或设为私密", result.getUserFriendlyMessage())
    }
}
