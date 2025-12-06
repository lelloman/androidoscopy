package com.lelloman.androidoscopy.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ThreadDataProviderTest {

    @Test
    fun `key is threads`() {
        val provider = ThreadDataProvider()
        assertEquals("threads", provider.key)
    }

    @Test
    fun `default interval is 5 seconds`() {
        val provider = ThreadDataProvider()
        assertEquals(5.seconds, provider.interval)
    }

    @Test
    fun `custom interval is respected`() {
        val provider = ThreadDataProvider(interval = 10.seconds)
        assertEquals(10.seconds, provider.interval)
    }

    @Test
    fun `collect returns active count`() = runTest {
        val provider = ThreadDataProvider()
        val result = provider.collect()

        assertTrue(result.containsKey("active_count"))
        assertTrue((result["active_count"] as Int) > 0)
    }

    @Test
    fun `collect returns total count`() = runTest {
        val provider = ThreadDataProvider()
        val result = provider.collect()

        assertTrue(result.containsKey("total_count"))
        assertTrue((result["total_count"] as Int) > 0)
    }

    @Test
    fun `collect returns state counts`() = runTest {
        val provider = ThreadDataProvider()
        val result = provider.collect()

        assertTrue(result.containsKey("state_counts"))
        @Suppress("UNCHECKED_CAST")
        val stateCounts = result["state_counts"] as Map<String, Int>
        assertNotNull(stateCounts)
    }

    @Test
    fun `collect returns threads list`() = runTest {
        val provider = ThreadDataProvider()
        val result = provider.collect()

        assertTrue(result.containsKey("threads"))
        @Suppress("UNCHECKED_CAST")
        val threads = result["threads"] as List<Map<String, Any>>
        assertTrue(threads.isNotEmpty())
    }

    @Test
    fun `thread info contains required fields`() = runTest {
        val provider = ThreadDataProvider()
        val result = provider.collect()

        @Suppress("UNCHECKED_CAST")
        val threads = result["threads"] as List<Map<String, Any>>
        val firstThread = threads.first()

        assertTrue(firstThread.containsKey("id"))
        assertTrue(firstThread.containsKey("name"))
        assertTrue(firstThread.containsKey("state"))
        assertTrue(firstThread.containsKey("priority"))
        assertTrue(firstThread.containsKey("is_daemon"))
        assertTrue(firstThread.containsKey("is_alive"))
    }

    @Test
    fun `threads list is sorted by name`() = runTest {
        val provider = ThreadDataProvider()
        val result = provider.collect()

        @Suppress("UNCHECKED_CAST")
        val threads = result["threads"] as List<Map<String, Any>>
        val names = threads.map { it["name"] as String }
        assertEquals(names, names.sorted())
    }
}
