package com.lelloman.androidoscopy.okhttp

import com.lelloman.androidoscopy.data.DataProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Data provider that exposes network request history to the Androidoscopy dashboard.
 */
class NetworkRequestDataProvider(
    private val interceptor: AndroidoscopyInterceptor
) : DataProvider {

    override val key: String = "network"
    override val interval: Duration = 1.seconds

    override suspend fun collect(): Map<String, Any> {
        val stats = interceptor.getStats()
        val requests = interceptor.getRequests()

        return mapOf(
            "stats" to stats.toMap(),
            "request_count" to requests.size,
            "requests" to requests.map { it.toMap() },
            "latest" to (requests.firstOrNull()?.toMap() ?: emptyMap<String, Any>())
        )
    }
}
