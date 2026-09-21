package com.douyin.downloader

import com.douyin.downloader.data.model.ResolverErrorCode
import com.douyin.downloader.data.model.ResolverException
import com.douyin.downloader.domain.resolver.RealDouyinResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealDouyinResolverTest {

    private val resolver = RealDouyinResolver()

    @Test
    fun testMockFallbackKeywords() = runTest {
        val result = resolver.resolve("https://v.douyin.com/mock_sample/")
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals("douyin", data?.platform)
        assertTrue(data?.media?.isNotEmpty() == true)
    }

    @Test
    fun testMockFallbackFailKeywords() = runTest {
        val result = resolver.resolve("https://v.douyin.com/fail_test/")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? ResolverException
        assertNotNull(ex)
        assertEquals(ResolverErrorCode.WORK_NOT_FOUND, ex?.code)
    }

    @Test
    fun testInvalidUrlDetection() = runTest {
        val result = resolver.resolve("这里没有任何可用的链接字符")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? ResolverException
        assertNotNull(ex)
        assertEquals(ResolverErrorCode.INVALID_URL, ex?.code)
    }
}
