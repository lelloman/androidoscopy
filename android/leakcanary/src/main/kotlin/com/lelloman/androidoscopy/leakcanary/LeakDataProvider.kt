package com.lelloman.androidoscopy.leakcanary

import com.lelloman.androidoscopy.data.DataProvider
import leakcanary.EventListener
import leakcanary.EventListener.Event
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Data provider that exposes LeakCanary leak reports to the dashboard.
 *
 * Usage:
 * ```kotlin
 * val leakDataProvider = LeakDataProvider()
 * LeakCanary.config = LeakCanary.config.copy(
 *     eventListeners = LeakCanary.config.eventListeners + leakDataProvider.eventListener
 * )
 * Androidoscopy.registerDataProvider(leakDataProvider)
 * ```
 */
class LeakDataProvider(
    private val maxHistory: Int = 20
) : DataProvider {

    override val key: String = "leaks"
    override val interval: Duration = 5.seconds

    private val leakHistory = CopyOnWriteArrayList<LeakRecord>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    /**
     * Event listener to register with LeakCanary.
     */
    val eventListener: EventListener = EventListener { event ->
        when (event) {
            is Event.HeapAnalysisDone<*> -> {
                val analysis = event.heapAnalysis
                // HeapAnalysis can be HeapAnalysisSuccess or HeapAnalysisFailure
                if (analysis.javaClass.simpleName == "HeapAnalysisSuccess") {
                    processAnalysis(analysis)
                }
            }
            else -> { /* Ignore other events */ }
        }
    }

    override suspend fun collect(): Map<String, Any> {
        return mapOf(
            "leaks" to leakHistory.map { it.toMap() },
            "leak_count" to leakHistory.size,
            "latest" to (leakHistory.firstOrNull()?.toMap() ?: emptyMap<String, Any>())
        )
    }

    private fun processAnalysis(analysis: Any) {
        val timestamp = dateFormat.format(Date())

        try {
            // Use reflection to access LeakCanary internals
            // since the Shark library types might not be directly available
            val allLeaksMethod = analysis.javaClass.getMethod("getAllLeaks")
            val allLeaks = allLeaksMethod.invoke(analysis) as? Iterable<*> ?: return

            for (leakGroup in allLeaks) {
                if (leakGroup == null) continue

                val leakClass = leakGroup.javaClass

                // Get signature
                val signature = try {
                    leakClass.getMethod("getSignature").invoke(leakGroup) as? String ?: ""
                } catch (e: Exception) { "" }

                // Get short description
                val shortDescription = try {
                    leakClass.getMethod("getShortDescription").invoke(leakGroup) as? String ?: ""
                } catch (e: Exception) { "" }

                // Get leaks list to count
                val leaks = try {
                    leakClass.getMethod("getLeaks").invoke(leakGroup) as? List<*>
                } catch (e: Exception) { null }

                val retainedCount = leaks?.size ?: 1

                // Get total retained heap size
                val retainedHeapSize = try {
                    leakClass.getMethod("getTotalRetainedHeapByteSize").invoke(leakGroup) as? Long ?: 0L
                } catch (e: Exception) { 0L }

                // Get leaking object class name from first leak
                val leakingObjectClass = try {
                    val firstLeak = leaks?.firstOrNull()
                    if (firstLeak != null) {
                        val leakTraces = firstLeak.javaClass.getMethod("getLeakTraces").invoke(firstLeak) as? List<*>
                        val firstTrace = leakTraces?.firstOrNull()
                        if (firstTrace != null) {
                            val leakingObject = firstTrace.javaClass.getMethod("getLeakingObject").invoke(firstTrace)
                            leakingObject?.javaClass?.getMethod("getClassName")?.invoke(leakingObject) as? String ?: "Unknown"
                        } else "Unknown"
                    } else "Unknown"
                } catch (e: Exception) { "Unknown" }

                val record = LeakRecord(
                    id = "leak_${System.currentTimeMillis()}_${signature.hashCode()}",
                    timestamp = timestamp,
                    signature = signature,
                    shortDescription = shortDescription,
                    leakingObjectClass = leakingObjectClass,
                    retainedCount = retainedCount,
                    retainedHeapByteSize = retainedHeapSize,
                    stackTrace = shortDescription // Use short description as stack trace summary
                )

                leakHistory.add(0, record)
                while (leakHistory.size > maxHistory) {
                    leakHistory.removeAt(leakHistory.size - 1)
                }
            }
        } catch (e: Exception) {
            // Silently fail if reflection doesn't work
        }
    }

    private data class LeakRecord(
        val id: String,
        val timestamp: String,
        val signature: String,
        val shortDescription: String,
        val leakingObjectClass: String,
        val retainedCount: Int,
        val retainedHeapByteSize: Long,
        val stackTrace: String
    ) {
        fun toMap(): Map<String, Any> = mapOf(
            "id" to id,
            "timestamp" to timestamp,
            "signature" to signature,
            "short_description" to shortDescription,
            "leaking_object_class" to leakingObjectClass,
            "retained_count" to retainedCount,
            "retained_heap_bytes" to retainedHeapByteSize,
            "stack_trace" to stackTrace
        )
    }
}
