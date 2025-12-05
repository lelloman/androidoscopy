package com.lelloman.androidoscopy.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class DataDebouncerTest {

    private lateinit var testScope: TestScope
    private var flushCount = 0

    @Before
    fun setUp() {
        testScope = TestScope(UnconfinedTestDispatcher())
        flushCount = 0
    }

    @Test
    fun `flush immediately invokes callback when scheduled`() {
        val debouncer = DataDebouncer(testScope, 100.milliseconds) {
            flushCount++
        }

        debouncer.schedule()
        debouncer.flush()

        assertEquals(1, flushCount)
    }

    @Test
    fun `flush does nothing if no pending data`() {
        val debouncer = DataDebouncer(testScope, 100.milliseconds) {
            flushCount++
        }

        debouncer.flush()
        assertEquals(0, flushCount)
    }

    @Test
    fun `cancel prevents flush`() {
        val debouncer = DataDebouncer(testScope, 100.milliseconds) {
            flushCount++
        }

        debouncer.schedule()
        debouncer.cancel()
        debouncer.flush()

        assertEquals(0, flushCount)
    }

    @Test
    fun `multiple flushes after single schedule only fires once`() {
        val debouncer = DataDebouncer(testScope, 100.milliseconds) {
            flushCount++
        }

        debouncer.schedule()
        debouncer.flush()
        debouncer.flush()
        debouncer.flush()

        assertEquals(1, flushCount)
    }

    @Test
    fun `schedule after flush allows another flush`() {
        val debouncer = DataDebouncer(testScope, 100.milliseconds) {
            flushCount++
        }

        debouncer.schedule()
        debouncer.flush()
        assertEquals(1, flushCount)

        debouncer.schedule()
        debouncer.flush()
        assertEquals(2, flushCount)
    }

    @Test
    fun `cancel after cancel is safe`() {
        val debouncer = DataDebouncer(testScope, 100.milliseconds) {
            flushCount++
        }

        debouncer.cancel()
        debouncer.cancel()
        assertEquals(0, flushCount)
    }
}
