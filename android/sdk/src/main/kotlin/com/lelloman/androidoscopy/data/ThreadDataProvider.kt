package com.lelloman.androidoscopy.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ThreadDataProvider(
    override val interval: Duration = 5.seconds
) : DataProvider {

    override val key = "threads"

    override suspend fun collect(): Map<String, Any> {
        val allThreads = Thread.getAllStackTraces().keys
        val activeCount = Thread.activeCount()

        val threadInfoList = allThreads.map { thread ->
            mapOf(
                "id" to thread.id,
                "name" to thread.name,
                "state" to thread.state.name,
                "priority" to thread.priority,
                "is_daemon" to thread.isDaemon,
                "is_alive" to thread.isAlive
            )
        }.sortedBy { it["name"] as String }

        val stateCount = allThreads.groupingBy { it.state.name }.eachCount()

        return mapOf(
            "active_count" to activeCount,
            "total_count" to allThreads.size,
            "state_counts" to stateCount,
            "threads" to threadInfoList
        )
    }
}
