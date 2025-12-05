package com.lelloman.androidoscopy.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

class DataProviderManager(
    private val scope: CoroutineScope,
    private val onData: (key: String, data: Map<String, Any>) -> Unit
) {
    private val providers = mutableListOf<DataProvider>()
    private val jobs = mutableMapOf<String, Job>()
    private var isRunning = false

    fun register(provider: DataProvider) {
        providers.add(provider)
        if (isRunning) {
            startProvider(provider)
        }
    }

    fun unregister(provider: DataProvider) {
        providers.remove(provider)
        jobs[provider.key]?.cancel()
        jobs.remove(provider.key)
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        for (provider in providers) {
            startProvider(provider)
        }
    }

    fun stop() {
        isRunning = false
        for ((_, job) in jobs) {
            job.cancel()
        }
        jobs.clear()
    }

    private fun startProvider(provider: DataProvider) {
        val job = scope.launch {
            while (isRunning) {
                try {
                    val data = provider.collect()
                    onData(provider.key, data)
                } catch (e: Exception) {
                    // Log error but continue running
                }
                delay(provider.interval)
            }
        }
        jobs[provider.key] = job
    }

    fun getRegisteredProviders(): List<DataProvider> = providers.toList()

    fun isRunning(): Boolean = isRunning
}
