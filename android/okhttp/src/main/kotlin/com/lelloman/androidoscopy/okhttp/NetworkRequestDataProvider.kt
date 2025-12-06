package com.lelloman.androidoscopy.okhttp

import com.lelloman.androidoscopy.data.DataProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Data provider that exposes network request history to the Androidoscopy dashboard.
 * Sends all requests to the server which handles accumulation and deduplication.
 */
class NetworkRequestDataProvider(
    private val interceptor: AndroidoscopyInterceptor
) : DataProvider {

    override val key: String = "network"
    override val interval: Duration = 1.seconds

    override suspend fun collect(): Map<String, Any> {
        val allRequests = interceptor.getRequests()
        val stats = interceptor.getStats()

        return mapOf(
            "stats" to stats.toMap(),
            "request_count" to allRequests.size,
            "requests" to allRequests.map { it.toMap() },
            "latest" to (allRequests.firstOrNull()?.toMap() ?: emptyMap<String, Any>())
        )
    }
}
