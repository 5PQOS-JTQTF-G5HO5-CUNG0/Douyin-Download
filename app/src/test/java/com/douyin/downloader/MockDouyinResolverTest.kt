package com.douyin.downloader

import com.douyin.downloader.data.model.ResolverErrorCode
import com.douyin.downloader.data.model.ResolverException
import com.douyin.downloader.domain.resolver.MockDouyinResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockDouyinResolverTest {

    private val resolver = MockDouyinResolver()

    @Test
    fun testMockSuccessScenario() = runTest {
        val result = resolver.resolve("https://v.douyin.com/abc123/")
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals("douyin", data?.platform)
        assertTrue(data?.media?.isNotEmpty() == true)
        assertEquals(MockDouyinResolver.SAMPLE_VIDEO_URL, data?.media?.first()?.url)
    }

    @Test
    fun testMockNotFoundScenario() = runTest {
        val result = resolver.resolve("https://v.douyin.com/video_notfound_test")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? ResolverException
        assertNotNull(ex)
        assertEquals(ResolverErrorCode.WORK_NOT_FOUND, ex?.code)
    }

    @Test
    fun testMockExpiredScenario() = runTest {
        val result = resolver.resolve("https://v.douyin.com/expired_video")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? ResolverException
        assertNotNull(ex)
        assertEquals(ResolverErrorCode.MEDIA_EXPIRED, ex?.code)
    }
}
