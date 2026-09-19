package com.lelloman.androidoscopy.session
import org.junit.Assert.*
import org.junit.Test

class SessionClockTest {
    @Test fun expiredSessionCannotBeRevived() {
        var now = 0L
        val clock = SessionClock { now }
        clock.start(1000)
        now = 999
        assertFalse(clock.isExpired())
        now = 1000
        assertTrue(clock.isExpired())
        assertFalse(clock.activity())
    }
    @Test fun onlyExplicitActivityExtendsDeadline() {
        var now = 0L
        val clock = SessionClock { now }
        clock.start(1000)
        now = 900
        assertTrue(clock.activity())
        now = 1100
        repeat(10) { assertEquals(800L, clock.remainingMs()); assertFalse(clock.isExpired()) }
        now = 1900
        assertTrue(clock.isExpired())
    }
    @Test fun debugNeverExpiresAndStopDisablesActivity() {
        var now = 0L
        val clock = SessionClock { now }
        clock.start(null)
        now = Long.MAX_VALUE
        assertFalse(clock.isExpired())
        assertNull(clock.remainingMs())
        clock.stop()
        assertFalse(clock.activity())
    }
    @Test fun matchesDesktopCommitmentVector() {
        assertEquals("80a09de3bfe30da90116e588ade2f812d49b55625be8b4abbff775fa5a5a74e9",
            PairingCrypto.hex(PairingCrypto.commitment(ByteArray(32), ByteArray(32) { 1 })))
    }
}
