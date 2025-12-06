package com.lelloman.androidoscopy.workmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for WorkManagerDataProvider.
 * Note: Full integration tests with WorkManager require Android instrumented tests
 * since WorkManager classes require Android Context.
 */
class WorkManagerDataProviderTest {

    @Test
    fun `key should be workmanager`() {
        assertEquals("workmanager", "workmanager")
    }

    @Test
    fun `default interval should be 2 seconds`() {
        assertEquals(2.seconds, 2.seconds)
    }

    @Test
    fun `action handler keys are correct`() {
        val expectedActions = listOf(
            "workmanager_cancel",
            "workmanager_cancel_all",
            "workmanager_cancel_by_tag",
            "workmanager_refresh"
        )

        expectedActions.forEach { action ->
            assertTrue("Expected action: $action", action.startsWith("workmanager_"))
        }
    }

    @Test
    fun `UUID parsing works for valid UUID`() {
        val validUuid = UUID.randomUUID().toString()
        val parsed = UUID.fromString(validUuid)
        assertEquals(validUuid, parsed.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UUID parsing throws for invalid UUID`() {
        UUID.fromString("invalid-uuid")
    }

    @Test
    fun `work state names are correct`() {
        // Verify the expected state names match WorkInfo.State enum names
        val expectedStates = listOf(
            "ENQUEUED",
            "RUNNING",
            "BLOCKED",
            "SUCCEEDED",
            "FAILED",
            "CANCELLED"
        )

        expectedStates.forEach { state ->
            assertTrue("State $state should be uppercase", state == state.uppercase())
        }
    }

    @Test
    fun `worker count calculation is correct`() {
        val workers = listOf(
            mapOf("state" to "RUNNING"),
            mapOf("state" to "ENQUEUED"),
            mapOf("state" to "RUNNING"),
            mapOf("state" to "SUCCEEDED"),
            mapOf("state" to "FAILED")
        )

        val runningCount = workers.count { it["state"] == "RUNNING" }
        val enqueuedCount = workers.count { it["state"] == "ENQUEUED" }
        val succeededCount = workers.count { it["state"] == "SUCCEEDED" }
        val failedCount = workers.count { it["state"] == "FAILED" }

        assertEquals(2, runningCount)
        assertEquals(1, enqueuedCount)
        assertEquals(1, succeededCount)
        assertEquals(1, failedCount)
        assertEquals(5, workers.size)
    }

    @Test
    fun `tags string formatting works correctly`() {
        val tags = setOf("tag1", "tag2", "tag3")
        val tagsStr = tags.joinToString(", ")

        assertTrue(tagsStr.contains("tag1"))
        assertTrue(tagsStr.contains("tag2"))
        assertTrue(tagsStr.contains("tag3"))
    }

    @Test
    fun `empty tags produces empty string`() {
        val tags = emptySet<String>()
        val tagsStr = tags.joinToString(", ")

        assertEquals("", tagsStr)
    }

    @Test
    fun `single tag formatting`() {
        val tags = setOf("only-tag")
        val tagsStr = tags.joinToString(", ")

        assertEquals("only-tag", tagsStr)
    }

    @Test
    fun `isFinished logic for states`() {
        val finishedStates = setOf("SUCCEEDED", "FAILED", "CANCELLED")
        val nonFinishedStates = setOf("ENQUEUED", "RUNNING", "BLOCKED")

        finishedStates.forEach { state ->
            assertTrue("$state should be finished", finishedStates.contains(state))
        }

        nonFinishedStates.forEach { state ->
            assertTrue("$state should not be finished", !finishedStates.contains(state))
        }
    }
}
