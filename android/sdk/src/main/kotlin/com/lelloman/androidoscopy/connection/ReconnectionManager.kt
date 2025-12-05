package com.lelloman.androidoscopy.connection

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ReconnectionManager(
    private val initialDelay: Duration = 1.seconds,
    private val maxDelay: Duration = 30.seconds,
    private val factor: Double = 2.0
) {
    private var currentDelay = initialDelay
    private var attemptCount = 0

    fun nextDelay(): Duration {
        val delay = currentDelay
        currentDelay = minOf(currentDelay * factor, maxDelay)
        attemptCount++
        return delay
    }

    fun reset() {
        currentDelay = initialDelay
        attemptCount = 0
    }

    fun getAttemptCount(): Int = attemptCount
}

private operator fun Duration.times(factor: Double): Duration {
    return (this.inWholeMilliseconds * factor).toLong().let { Duration.parse("${it}ms") }
}
