package com.lelloman.androidoscopy.session

/** The native check uses simple-server's single-unit replenishing Budget. */
internal object PairingRateLimit {
    init { System.loadLibrary("androidoscopy_pairing_rate_limit") }

    external fun nativeAdmit(lastAdmittedMs: Long, nowMs: Long): Boolean
}

/** One global gate per SessionRuntime, retained through start and stop. */
internal class PairingAttemptGate {
    private var lastAdmittedMs = Long.MIN_VALUE / 2

    fun admit(nowMs: Long): Boolean {
        if (!PairingRateLimit.nativeAdmit(lastAdmittedMs, nowMs)) return false
        lastAdmittedMs = nowMs
        return true
    }
}
