package com.lelloman.androidoscopy.logging

import android.util.Log
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.protocol.LogLevel
import timber.log.Timber

/**
 * A Timber tree that forwards log messages to Androidoscopy.
 *
 * To use this tree, add Timber to your project and plant this tree:
 * ```kotlin
 * Timber.plant(AndroidoscopyTree())
 * ```
 *
 * All subsequent Timber log calls will be forwarded to the Androidoscopy service.
 */
class AndroidoscopyTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = priorityToLogLevel(priority)
        Androidoscopy.log(level, tag, message, t)
    }

    private fun priorityToLogLevel(priority: Int): LogLevel {
        return when (priority) {
            Log.VERBOSE -> LogLevel.VERBOSE
            Log.DEBUG -> LogLevel.DEBUG
            Log.INFO -> LogLevel.INFO
            Log.WARN -> LogLevel.WARN
            Log.ERROR -> LogLevel.ERROR
            Log.ASSERT -> LogLevel.ERROR
            else -> LogLevel.DEBUG
        }
    }
}
