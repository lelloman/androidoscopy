package com.lelloman.androidoscopy.data

import kotlin.time.Duration

interface DataProvider {
    val key: String
    val interval: Duration
    suspend fun collect(): Map<String, Any>
    /** Release listeners, threads and transient buffers when a session ends. */
    fun close() {}
}
