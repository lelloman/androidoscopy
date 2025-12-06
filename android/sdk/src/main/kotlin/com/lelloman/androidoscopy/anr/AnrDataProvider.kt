package com.lelloman.androidoscopy.anr

import com.lelloman.androidoscopy.data.DataProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Data provider that manages ANR detection and exposes ANR history to the dashboard.
 */
class AnrDataProvider(
    thresholdMs: Long = AnrWatchdog.DEFAULT_THRESHOLD_MS,
    maxHistory: Int = 10
) : DataProvider, AnrListener {

    override val key: String = "anr"
    override val interval: Duration = 1.seconds

    private val anrHistory = CopyOnWriteArrayList<AnrRecord>()
    private val maxHistorySize = maxHistory

    private val watchdog = AnrWatchdog(
        thresholdMs = thresholdMs,
        listener = this
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    private var isStarted = false

    fun start() {
        if (!isStarted) {
            watchdog.start()
            isStarted = true
        }
    }

    fun stop() {
        watchdog.stop()
        isStarted = false
    }

    override suspend fun collect(): Map<String, Any> {
        // Ensure watchdog is running when data is being collected
        if (!isStarted) {
            start()
        }

        return mapOf(
            "count" to anrHistory.size,
            "latest" to (anrHistory.firstOrNull()?.toMap() ?: emptyMap<String, Any>()),
            "history" to anrHistory.map { it.toMap() }
        )
    }

    override fun onAnrDetected(anrInfo: AnrInfo) {
        val record = AnrRecord(
            id = "anr_${anrInfo.timestamp}",
            timestamp = dateFormat.format(Date(anrInfo.timestamp)),
            durationMs = anrInfo.durationMs,
            mainThreadTrace = formatStackTrace(anrInfo.mainThreadStackTrace),
            threadCount = anrInfo.allThreads.size,
            // Include top 5 threads by activity for deadlock detection
            suspiciousThreads = anrInfo.allThreads
                .filter { it.state == "BLOCKED" || it.state == "WAITING" }
                .take(5)
                .map { thread ->
                    mapOf(
                        "name" to thread.name,
                        "state" to thread.state,
                        "trace" to formatStackTrace(thread.stackTrace.take(10))
                    )
                }
        )

        // Add to history (FIFO)
        anrHistory.add(0, record)
        while (anrHistory.size > maxHistorySize) {
            anrHistory.removeAt(anrHistory.size - 1)
        }
    }

    private fun formatStackTrace(trace: List<StackTraceElement>): String {
        return trace.joinToString("\n") { "    at ${it.format()}" }
    }

    private data class AnrRecord(
        val id: String,
        val timestamp: String,
        val durationMs: Long,
        val mainThreadTrace: String,
        val threadCount: Int,
        val suspiciousThreads: List<Map<String, Any>>
    ) {
        fun toMap(): Map<String, Any> = mapOf(
            "id" to id,
            "timestamp" to timestamp,
            "duration_ms" to durationMs,
            "main_thread_trace" to mainThreadTrace,
            "thread_count" to threadCount,
            "suspicious_threads" to suspiciousThreads
        )
    }
}
