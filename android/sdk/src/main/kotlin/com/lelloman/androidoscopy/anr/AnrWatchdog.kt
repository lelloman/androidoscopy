package com.lelloman.androidoscopy.anr

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ANR (Application Not Responding) detection using a watchdog thread pattern.
 *
 * Posts a simple task to the main thread and checks if it completes within the threshold.
 * If not, captures stack traces and reports the ANR.
 */
class AnrWatchdog(
    private val thresholdMs: Long = DEFAULT_THRESHOLD_MS,
    private val checkIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS,
    private val listener: AnrListener
) {
    companion object {
        const val DEFAULT_THRESHOLD_MS = 4000L
        const val DEFAULT_CHECK_INTERVAL_MS = 1000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchdogThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private val responded = AtomicBoolean(true)
    private var lastTickTime = System.currentTimeMillis()

    fun start() {
        if (isRunning.getAndSet(true)) return

        watchdogThread = Thread({
            while (isRunning.get()) {
                responded.set(false)
                lastTickTime = System.currentTimeMillis()

                // Post a simple task to main thread
                mainHandler.post {
                    responded.set(true)
                }

                try {
                    Thread.sleep(checkIntervalMs)
                } catch (e: InterruptedException) {
                    break
                }

                // Check if main thread responded
                val elapsed = System.currentTimeMillis() - lastTickTime
                if (!responded.get() && elapsed >= thresholdMs) {
                    reportAnr(elapsed)
                }
            }
        }, "AnrWatchdog").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        watchdogThread?.interrupt()
        watchdogThread = null
    }

    private fun reportAnr(durationMs: Long) {
        val mainThread = Looper.getMainLooper().thread
        val mainStackTrace = mainThread.stackTrace

        val allThreads = Thread.getAllStackTraces()
            .filter { (thread, _) -> thread.isAlive }
            .map { (thread, stackTrace) ->
                ThreadInfo(
                    id = thread.id,
                    name = thread.name,
                    state = thread.state.name,
                    isMain = thread == mainThread,
                    stackTrace = stackTrace.map { element ->
                        StackTraceElement(
                            className = element.className,
                            methodName = element.methodName,
                            fileName = element.fileName,
                            lineNumber = element.lineNumber
                        )
                    }
                )
            }

        val anrInfo = AnrInfo(
            timestamp = System.currentTimeMillis(),
            durationMs = durationMs,
            mainThreadStackTrace = mainStackTrace.map { element ->
                StackTraceElement(
                    className = element.className,
                    methodName = element.methodName,
                    fileName = element.fileName,
                    lineNumber = element.lineNumber
                )
            },
            allThreads = allThreads
        )

        listener.onAnrDetected(anrInfo)
    }
}

data class AnrInfo(
    val timestamp: Long,
    val durationMs: Long,
    val mainThreadStackTrace: List<StackTraceElement>,
    val allThreads: List<ThreadInfo>
)

data class ThreadInfo(
    val id: Long,
    val name: String,
    val state: String,
    val isMain: Boolean,
    val stackTrace: List<StackTraceElement>
)

data class StackTraceElement(
    val className: String,
    val methodName: String,
    val fileName: String?,
    val lineNumber: Int
) {
    fun format(): String {
        val location = if (fileName != null && lineNumber >= 0) {
            "($fileName:$lineNumber)"
        } else if (fileName != null) {
            "($fileName)"
        } else {
            "(Unknown Source)"
        }
        return "$className.$methodName$location"
    }
}

fun interface AnrListener {
    fun onAnrDetected(anrInfo: AnrInfo)
}
