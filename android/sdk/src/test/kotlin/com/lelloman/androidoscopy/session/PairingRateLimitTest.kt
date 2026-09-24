package com.lelloman.androidoscopy.session

import org.junit.Assert.*
import org.junit.Test

/** Loads the host JNI library and exercises the method used by SessionRuntime. */
class PairingRateLimitTest {
    @Test fun nativeGateRetainsLastAdmissionAcrossDenials() {
        val gate = PairingAttemptGate()

        assertTrue(gate.admit(100))
        assertFalse(gate.admit(101))
        assertFalse(gate.admit(5_099))
        assertTrue(gate.admit(5_100))
        assertFalse(gate.admit(5_100))
        assertTrue(gate.admit(10_100))
    }

    @Test fun separateRuntimeGatesStartIndependentlyAndBackwardTimeIsRejected() {
        val first = PairingAttemptGate()
        val second = PairingAttemptGate()
        assertTrue(first.admit(9_000))
        assertTrue(second.admit(9_000))
        assertFalse(first.admit(8_000))
        assertFalse(first.admit(13_999))
        assertTrue(first.admit(14_000))
    }

    @Test fun nativeGateRejectsInvalidTimesAndHandlesLargeElapsedTime() {
        val neverAdmitted = Long.MIN_VALUE / 2
        assertFalse(PairingRateLimit.nativeAdmit(neverAdmitted, -1))
        assertFalse(PairingRateLimit.nativeAdmit(-1, 100))
        assertTrue(PairingRateLimit.nativeAdmit(neverAdmitted, Long.MAX_VALUE - 5_000))
        assertFalse(PairingRateLimit.nativeAdmit(Long.MAX_VALUE - 5_000, Long.MAX_VALUE - 1))
        assertTrue(PairingRateLimit.nativeAdmit(Long.MAX_VALUE - 5_000, Long.MAX_VALUE))
    }
}
