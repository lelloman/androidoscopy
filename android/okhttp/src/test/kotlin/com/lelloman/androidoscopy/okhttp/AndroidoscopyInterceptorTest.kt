package com.lelloman.androidoscopy.okhttp

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidoscopyInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var interceptor: AndroidoscopyInterceptor
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        interceptor = AndroidoscopyInterceptor(maxHistory = 10)
        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `intercept captures successful request`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        val response = client.newCall(request).execute()
        response.close()

        val requests = interceptor.getRequests()
        assertEquals(1, requests.size)

        val captured = requests[0]
        assertEquals("GET", captured.method)
        assertEquals("/test", captured.path)
        assertEquals(200, captured.responseCode)
        assertNull(captured.error)
        assertTrue(captured.isSuccess)
        assertTrue(captured.durationMs >= 0)
    }

    @Test
    fun `intercept captures error response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        val request = Request.Builder()
            .url(mockWebServer.url("/error"))
            .build()

        val response = client.newCall(request).execute()
        response.close()

        val requests = interceptor.getRequests()
        assertEquals(1, requests.size)

        val captured = requests[0]
        assertEquals(500, captured.responseCode)
        assertTrue(captured.isError)
    }

    @Test
    fun `intercept captures request headers`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/headers"))
            .header("X-Custom-Header", "test-value")
            .build()

        val response = client.newCall(request).execute()
        response.close()

        val requests = interceptor.getRequests()
        assertEquals(1, requests.size)
        assertEquals("test-value", requests[0].requestHeaders["X-Custom-Header"])
    }

    @Test
    fun `intercept captures response headers`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("X-Response-Header", "response-value")
        )

        val request = Request.Builder()
            .url(mockWebServer.url("/response-headers"))
            .build()

        val response = client.newCall(request).execute()
        response.close()

        val requests = interceptor.getRequests()
        assertEquals(1, requests.size)
        assertEquals("response-value", requests[0].responseHeaders["X-Response-Header"])
    }

    @Test
    fun `getStats returns correct statistics`() {
        // 2 success, 1 error
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(201))
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        repeat(3) {
            val request = Request.Builder()
                .url(mockWebServer.url("/stats"))
                .build()
            client.newCall(request).execute().close()
        }

        val stats = interceptor.getStats()
        assertEquals(3, stats.totalRequests)
        assertEquals(2, stats.successCount)
        assertEquals(1, stats.errorCount)
    }

    @Test
    fun `history respects maxHistory limit`() {
        repeat(15) {
            mockWebServer.enqueue(MockResponse().setResponseCode(200))
        }

        repeat(15) {
            val request = Request.Builder()
                .url(mockWebServer.url("/limit"))
                .build()
            client.newCall(request).execute().close()
        }

        val requests = interceptor.getRequests()
        assertEquals(10, requests.size) // maxHistory = 10
    }

    @Test
    fun `clear removes all requests`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/clear"))
            .build()
        client.newCall(request).execute().close()

        assertEquals(1, interceptor.getRequests().size)

        interceptor.clear()

        assertEquals(0, interceptor.getRequests().size)
    }

    @Test
    fun `HttpRequestInfo toMap contains all fields`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("body"))

        val request = Request.Builder()
            .url(mockWebServer.url("/tomap"))
            .build()
        client.newCall(request).execute().close()

        val captured = interceptor.getRequests()[0]
        val map = captured.toMap()

        assertNotNull(map["id"])
        assertNotNull(map["timestamp"])
        assertEquals("GET", map["method"])
        assertEquals("/tomap", map["path"])
        assertEquals(200, map["response_code"])
        assertNotNull(map["duration_ms"])
        assertEquals(true, map["is_success"])
        assertEquals(false, map["is_error"])
    }

    @Test
    fun `dataProvider returns correct data structure`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/provider"))
            .build()
        client.newCall(request).execute().close()

        val dataProvider = interceptor.dataProvider
        assertEquals("network", dataProvider.key)

        // We can't easily test collect() here since it's a suspend function
        // and we'd need a coroutine context
    }

    @Test
    fun `shared instance works correctly`() {
        val instance1 = AndroidoscopyInterceptor.instance
        val instance2 = AndroidoscopyInterceptor.instance

        assertTrue(instance1 === instance2)
    }
}
