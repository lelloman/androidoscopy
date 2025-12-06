package com.lelloman.androidoscopy.timber

import android.util.Log
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.protocol.LogLevel
import timber.log.Timber

/**
 * A Timber Tree that sends logs to Androidoscopy dashboard.
 *
 * Usage:
 * ```kotlin
 * Timber.plant(AndroidoscopyTree())
 * ```
 *
 * This will pipe all Timber logs to the Androidoscopy dashboard while
 * maintaining normal Timber functionality with other planted trees.
 *
 * @param minPriority Minimum log priority to forward to Androidoscopy.
 *                   Defaults to [Log.DEBUG]. Use [Log.VERBOSE] to capture all logs.
 */
class AndroidoscopyTree(
    private val minPriority: Int = Log.DEBUG
) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < minPriority) return

        val level = mapPriorityToLogLevel(priority)
        Androidoscopy.log(level, tag, message, t)
    }

    private fun mapPriorityToLogLevel(priority: Int): LogLevel = when (priority) {
        Log.VERBOSE -> LogLevel.VERBOSE
        Log.DEBUG -> LogLevel.DEBUG
        Log.INFO -> LogLevel.INFO
        Log.WARN -> LogLevel.WARN
        Log.ERROR, Log.ASSERT -> LogLevel.ERROR
        else -> LogLevel.DEBUG
    }
}
