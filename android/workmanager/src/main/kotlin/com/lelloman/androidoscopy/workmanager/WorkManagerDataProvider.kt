package com.lelloman.androidoscopy.workmanager

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.lelloman.androidoscopy.ActionResult
import com.lelloman.androidoscopy.data.DataProvider
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Data provider that exposes WorkManager work information to the dashboard.
 * Shows worker status, tags, constraints, and provides cancel action.
 *
 * Usage:
 * ```kotlin
 * val workManagerDataProvider = WorkManagerDataProvider(context)
 * Androidoscopy.registerDataProvider(workManagerDataProvider)
 * ```
 */
class WorkManagerDataProvider(
    private val context: Context
) : DataProvider {

    override val key: String = "workmanager"
    override val interval: Duration = 2.seconds

    private val workManager = WorkManager.getInstance(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    /**
     * Get action handlers for WorkManager operations.
     */
    fun getActionHandlers(): Map<String, suspend (Map<String, Any>) -> ActionResult> = mapOf(
        "workmanager_cancel" to ::handleCancel,
        "workmanager_cancel_all" to ::handleCancelAll,
        "workmanager_cancel_by_tag" to ::handleCancelByTag,
        "workmanager_refresh" to ::handleRefresh
    )

    override suspend fun collect(): Map<String, Any> {
        val allWork = workManager.getWorkInfosFlow(
            WorkQuery.Builder.fromStates(
                listOf(
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.BLOCKED,
                    WorkInfo.State.SUCCEEDED,
                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED
                )
            ).build()
        ).first()

        val workList = allWork.map { workInfo ->
            mapOf(
                "id" to workInfo.id.toString(),
                "state" to workInfo.state.name,
                "tags" to workInfo.tags.toList(),
                "tags_str" to workInfo.tags.joinToString(", "),
                "attempt_count" to workInfo.runAttemptCount,
                "generation" to workInfo.generation,
                "output_data" to workInfo.outputData.keyValueMap.map { (k, v) ->
                    "$k: $v"
                }.joinToString(", "),
                "is_running" to (workInfo.state == WorkInfo.State.RUNNING),
                "is_finished" to workInfo.state.isFinished,
                "stop_reason" to (workInfo.stopReason.takeIf { it != WorkInfo.STOP_REASON_NOT_STOPPED }?.toString() ?: "")
            )
        }

        val running = workList.count { it["state"] == "RUNNING" }
        val enqueued = workList.count { it["state"] == "ENQUEUED" }
        val succeeded = workList.count { it["state"] == "SUCCEEDED" }
        val failed = workList.count { it["state"] == "FAILED" }

        return mapOf(
            "workers" to workList,
            "worker_count" to workList.size,
            "running_count" to running,
            "enqueued_count" to enqueued,
            "succeeded_count" to succeeded,
            "failed_count" to failed
        )
    }

    private suspend fun handleCancel(args: Map<String, Any>): ActionResult {
        val workId = args["id"] as? String
            ?: return ActionResult.failure("Missing work ID")

        return try {
            val uuid = UUID.fromString(workId)
            workManager.cancelWorkById(uuid)
            ActionResult.success("Work cancelled: $workId")
        } catch (e: Exception) {
            ActionResult.failure("Failed to cancel: ${e.message}")
        }
    }

    private suspend fun handleCancelAll(args: Map<String, Any>): ActionResult {
        return try {
            workManager.cancelAllWork()
            ActionResult.success("All work cancelled")
        } catch (e: Exception) {
            ActionResult.failure("Failed to cancel all: ${e.message}")
        }
    }

    private suspend fun handleCancelByTag(args: Map<String, Any>): ActionResult {
        val tag = args["tag"] as? String
            ?: return ActionResult.failure("Missing tag")

        return try {
            workManager.cancelAllWorkByTag(tag)
            ActionResult.success("Work cancelled for tag: $tag")
        } catch (e: Exception) {
            ActionResult.failure("Failed to cancel by tag: ${e.message}")
        }
    }

    private suspend fun handleRefresh(args: Map<String, Any>): ActionResult {
        return ActionResult.success("Refreshed", collect())
    }
}
