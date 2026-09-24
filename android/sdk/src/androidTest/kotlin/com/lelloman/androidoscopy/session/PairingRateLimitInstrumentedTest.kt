package com.lelloman.androidoscopy.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingRateLimitInstrumentedTest {
    @Test fun packagedDeviceLibraryChecksFiveSecondBoundary() {
        val gate = PairingAttemptGate()
        assertTrue(gate.admit(1_000))
        assertFalse(gate.admit(5_999))
        assertTrue(gate.admit(6_000))
    }
}
