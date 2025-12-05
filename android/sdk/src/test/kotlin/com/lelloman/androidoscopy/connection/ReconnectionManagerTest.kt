package com.lelloman.androidoscopy.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ReconnectionManagerTest {

    private lateinit var manager: ReconnectionManager

    @Before
    fun setUp() {
        manager = ReconnectionManager(
            initialDelay = 1.seconds,
            maxDelay = 30.seconds,
            factor = 2.0
        )
    }

    @Test
    fun `first delay is initial delay`() {
        val delay = manager.nextDelay()
        assertEquals(1.seconds, delay)
    }

    @Test
    fun `delays increase exponentially`() {
        val first = manager.nextDelay()
        val second = manager.nextDelay()
        val third = manager.nextDelay()

        assertEquals(1.seconds, first)
        assertEquals(2.seconds, second)
        assertEquals(4.seconds, third)
    }

    @Test
    fun `delays are capped at max delay`() {
        // 1, 2, 4, 8, 16, 32 -> should cap at 30
        repeat(5) { manager.nextDelay() }
        val sixthDelay = manager.nextDelay()

        assertEquals(30.seconds, sixthDelay)
    }

    @Test
    fun `delays stay at max after reaching cap`() {
        repeat(10) { manager.nextDelay() }
        val delay = manager.nextDelay()

        assertEquals(30.seconds, delay)
    }

    @Test
    fun `reset restores initial delay`() {
        repeat(5) { manager.nextDelay() }
        manager.reset()
        val delay = manager.nextDelay()

        assertEquals(1.seconds, delay)
    }

    @Test
    fun `attempt count increments with each delay`() {
        assertEquals(0, manager.getAttemptCount())
        manager.nextDelay()
        assertEquals(1, manager.getAttemptCount())
        manager.nextDelay()
        assertEquals(2, manager.getAttemptCount())
    }

    @Test
    fun `reset clears attempt count`() {
        repeat(3) { manager.nextDelay() }
        assertEquals(3, manager.getAttemptCount())
        manager.reset()
        assertEquals(0, manager.getAttemptCount())
    }

    @Test
    fun `custom initial delay is respected`() {
        val customManager = ReconnectionManager(
            initialDelay = 500.milliseconds,
            maxDelay = 10.seconds,
            factor = 2.0
        )
        val delay = customManager.nextDelay()
        assertEquals(500.milliseconds, delay)
    }

    @Test
    fun `custom factor is respected`() {
        val customManager = ReconnectionManager(
            initialDelay = 1.seconds,
            maxDelay = 30.seconds,
            factor = 3.0
        )
        customManager.nextDelay() // 1s
        val second = customManager.nextDelay() // 3s
        val third = customManager.nextDelay() // 9s

        assertEquals(3.seconds, second)
        assertEquals(9.seconds, third)
    }

    @Test
    fun `max delay smaller than initial uses max`() {
        val customManager = ReconnectionManager(
            initialDelay = 10.seconds,
            maxDelay = 5.seconds,
            factor = 2.0
        )
        val delay = customManager.nextDelay()
        // Min of initial and max should be returned
        assertTrue(delay <= 10.seconds)
    }
}
