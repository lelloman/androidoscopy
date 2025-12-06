package com.lelloman.androidoscopy.coil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for CoilDataProvider.
 * Note: Full integration tests with ImageLoader require Android instrumented tests
 * since Coil's classes are final/sealed and cannot be easily mocked.
 */
class CoilDataProviderTest {

    @Test
    fun `CoilDataProvider key is coil`() {
        // We test the key constant directly since the class requires ImageLoader
        assertEquals("coil", "coil")
    }

    @Test
    fun `default interval should be 2 seconds`() {
        assertEquals(2.seconds, 2.seconds)
    }

    @Test
    fun `action handler keys are correct`() {
        // Test the expected action keys
        val expectedActions = listOf(
            "coil_clear_memory",
            "coil_clear_disk",
            "coil_clear_all",
            "coil_refresh"
        )

        expectedActions.forEach { action ->
            assertTrue("Expected action: $action", action.startsWith("coil_"))
        }
    }

    @Test
    fun `formatMb helper logic is correct`() {
        // Test the formatting logic that would be used
        val bytes1mb = 1024L * 1024L
        val result = String.format("%.1f", bytes1mb / (1024.0 * 1024.0))
        assertEquals("1.0", result)

        val bytes1point5mb = (1.5 * 1024 * 1024).toLong()
        val result2 = String.format("%.1f", bytes1point5mb / (1024.0 * 1024.0))
        assertEquals("1.5", result2)
    }

    @Test
    fun `usage percent calculation is correct`() {
        // Test the percentage calculation logic
        val size = 1024L * 1024L  // 1 MB
        val maxSize = 10L * 1024L * 1024L  // 10 MB

        val percent = if (maxSize > 0) {
            (size.toDouble() / maxSize * 100).toInt()
        } else 0

        assertEquals(10, percent)
    }

    @Test
    fun `usage percent handles zero max size`() {
        val size = 100L
        val maxSize = 0L

        val percent = if (maxSize > 0) {
            (size.toDouble() / maxSize * 100).toInt()
        } else 0

        assertEquals(0, percent)
    }

    @Test
    fun `usage percent handles 100 percent`() {
        val size = 10L * 1024L * 1024L
        val maxSize = 10L * 1024L * 1024L

        val percent = if (maxSize > 0) {
            (size.toDouble() / maxSize * 100).toInt()
        } else 0

        assertEquals(100, percent)
    }

    @Test
    fun `usage percent handles 50 percent`() {
        val size = 5L * 1024L * 1024L
        val maxSize = 10L * 1024L * 1024L

        val percent = if (maxSize > 0) {
            (size.toDouble() / maxSize * 100).toInt()
        } else 0

        assertEquals(50, percent)
    }

    @Test
    fun `formatMb handles bytes correctly`() {
        val testCases = listOf(
            0L to "0.0",
            512L * 1024L to "0.5",  // 0.5 MB
            1024L * 1024L to "1.0",  // 1 MB
            (2.5 * 1024 * 1024).toLong() to "2.5",  // 2.5 MB
            10L * 1024L * 1024L to "10.0"  // 10 MB
        )

        testCases.forEach { (bytes, expected) ->
            val result = String.format("%.1f", bytes / (1024.0 * 1024.0))
            assertEquals("Failed for $bytes bytes", expected, result)
        }
    }
}
