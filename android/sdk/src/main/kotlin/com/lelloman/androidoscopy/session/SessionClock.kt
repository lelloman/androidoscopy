package com.lelloman.androidoscopy.session

/** A monotonic deadline; transport traffic cannot extend it. */
internal class SessionClock(private val now: () -> Long) {
    private var timeout: Long? = null
    private var deadline = 0L
    @Volatile var active = false
        private set

    @Synchronized fun start(timeoutMs: Long?) {
        require(timeoutMs == null || timeoutMs in 1..86_400_000L)
        timeout = timeoutMs
        active = true
        deadline = now() + (timeoutMs ?: 0L)
    }

    @Synchronized fun isExpired(): Boolean = active && timeout != null && now() >= deadline

    @Synchronized fun activity(): Boolean {
        if (!active || isExpired()) return false
        timeout?.let { deadline = now() + it }
        return true
    }

    @Synchronized fun remainingMs(): Long? = timeout?.let { (deadline - now()).coerceAtLeast(0) }

    @Synchronized fun stop() { active = false }
}
