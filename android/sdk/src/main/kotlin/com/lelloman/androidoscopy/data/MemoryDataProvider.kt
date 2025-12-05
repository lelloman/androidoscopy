package com.lelloman.androidoscopy.data

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MemoryDataProvider(
    private val context: Context,
    override val interval: Duration = 5.seconds
) : DataProvider {

    override val key = "memory"

    override suspend fun collect(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val heapUsed = runtime.totalMemory() - runtime.freeMemory()
        val heapMax = runtime.maxMemory()

        return mapOf(
            "heap_used_bytes" to heapUsed,
            "heap_max_bytes" to heapMax,
            "heap_free_bytes" to runtime.freeMemory(),
            "heap_total_bytes" to runtime.totalMemory(),
            "native_heap_size" to Debug.getNativeHeapSize(),
            "native_heap_allocated" to Debug.getNativeHeapAllocatedSize(),
            "native_heap_free" to Debug.getNativeHeapFreeSize(),
            "system_available_bytes" to memoryInfo.availMem,
            "system_total_bytes" to memoryInfo.totalMem,
            "low_memory" to memoryInfo.lowMemory,
            "pressure_level" to getMemoryPressure(heapUsed, heapMax, memoryInfo)
        )
    }

    private fun getMemoryPressure(
        heapUsed: Long,
        heapMax: Long,
        memoryInfo: ActivityManager.MemoryInfo
    ): String {
        val heapUsagePercent = heapUsed.toDouble() / heapMax.toDouble()

        return when {
            memoryInfo.lowMemory -> "CRITICAL"
            heapUsagePercent >= 0.9 -> "CRITICAL"
            heapUsagePercent >= 0.75 -> "HIGH"
            heapUsagePercent >= 0.5 -> "MODERATE"
            else -> "LOW"
        }
    }
}
