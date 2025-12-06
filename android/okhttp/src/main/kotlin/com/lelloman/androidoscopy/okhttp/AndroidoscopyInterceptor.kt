package com.lelloman.androidoscopy.okhttp

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * OkHttp Interceptor that captures HTTP request/response information for Androidoscopy.
 *
 * Usage:
 * ```kotlin
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(AndroidoscopyInterceptor.instance)
 *     .build()
 * ```
 *
 * Or create your own instance:
 * ```kotlin
 * val interceptor = AndroidoscopyInterceptor(maxHistory = 200)
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(interceptor)
 *     .build()
 *
 * // Register with Androidoscopy
 * Androidoscopy.registerDataProvider(interceptor.dataProvider)
 * ```
 */
class AndroidoscopyInterceptor(
    private val maxHistory: Int = DEFAULT_MAX_HISTORY
) : Interceptor {

    private val requestHistory = CopyOnWriteArrayList<HttpRequestInfo>()

    /**
     * Data provider for exposing network requests to the dashboard.
     */
    val dataProvider: NetworkRequestDataProvider by lazy {
        NetworkRequestDataProvider(this)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        val url = request.url
        val requestHeaders = mutableMapOf<String, String>()
        request.headers.forEach { (name, value) ->
            requestHeaders[name] = value
        }

        val requestBodySize = try {
            request.body?.contentLength() ?: 0L
        } catch (e: IOException) {
            -1L
        }

        return try {
            val response = chain.proceed(request)
            val endTime = System.currentTimeMillis()

            val responseHeaders = mutableMapOf<String, String>()
            response.headers.forEach { (name, value) ->
                responseHeaders[name] = value
            }

            val responseBodySize = response.body?.contentLength() ?: -1L

            val requestInfo = HttpRequestInfo(
                id = requestId,
                timestamp = startTime,
                method = request.method,
                url = url.toString(),
                host = url.host,
                path = url.encodedPath,
                requestHeaders = requestHeaders,
                requestBodySize = requestBodySize,
                responseCode = response.code,
                responseMessage = response.message,
                responseHeaders = responseHeaders,
                responseBodySize = responseBodySize,
                durationMs = endTime - startTime,
                error = null
            )

            addToHistory(requestInfo)
            response
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()

            val requestInfo = HttpRequestInfo(
                id = requestId,
                timestamp = startTime,
                method = request.method,
                url = url.toString(),
                host = url.host,
                path = url.encodedPath,
                requestHeaders = requestHeaders,
                requestBodySize = requestBodySize,
                responseCode = null,
                responseMessage = null,
                responseHeaders = emptyMap(),
                responseBodySize = 0L,
                durationMs = endTime - startTime,
                error = e.message ?: e.javaClass.simpleName
            )

            addToHistory(requestInfo)
            throw e
        }
    }

    private fun addToHistory(info: HttpRequestInfo) {
        requestHistory.add(0, info)
        while (requestHistory.size > maxHistory) {
            requestHistory.removeAt(requestHistory.size - 1)
        }
    }

    /**
     * Get all captured requests.
     */
    fun getRequests(): List<HttpRequestInfo> = requestHistory.toList()

    /**
     * Get statistics about captured requests.
     */
    fun getStats(): NetworkStats {
        val requests = requestHistory.toList()
        val successCount = requests.count { it.isSuccess }
        val errorCount = requests.count { it.isError }
        val totalDuration = requests.sumOf { it.durationMs }
        val avgDuration = if (requests.isNotEmpty()) totalDuration / requests.size else 0L

        return NetworkStats(
            totalRequests = requests.size,
            successCount = successCount,
            errorCount = errorCount,
            averageDurationMs = avgDuration
        )
    }

    /**
     * Clear all captured requests.
     */
    fun clear() {
        requestHistory.clear()
    }

    companion object {
        const val DEFAULT_MAX_HISTORY = 100

        /**
         * Shared instance for convenience.
         */
        val instance: AndroidoscopyInterceptor by lazy {
            AndroidoscopyInterceptor()
        }
    }
}

/**
 * Network statistics summary.
 */
data class NetworkStats(
    val totalRequests: Int,
    val successCount: Int,
    val errorCount: Int,
    val averageDurationMs: Long
) {
    fun toMap(): Map<String, Any> = mapOf(
        "total_requests" to totalRequests,
        "success_count" to successCount,
        "error_count" to errorCount,
        "average_duration_ms" to averageDurationMs
    )
}
