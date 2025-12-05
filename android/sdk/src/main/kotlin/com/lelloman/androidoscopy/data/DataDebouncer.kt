package com.lelloman.androidoscopy.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class DataDebouncer(
    private val scope: CoroutineScope,
    private val debounceWindow: Duration = 100.milliseconds,
    private val onFlush: () -> Unit
) {
    private var pendingJob: Job? = null
    private var hasPendingData = false

    fun schedule() {
        hasPendingData = true
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(debounceWindow)
            if (hasPendingData) {
                hasPendingData = false
                onFlush()
            }
        }
    }

    fun flush() {
        pendingJob?.cancel()
        if (hasPendingData) {
            hasPendingData = false
            onFlush()
        }
    }

    fun cancel() {
        pendingJob?.cancel()
        hasPendingData = false
    }
}
