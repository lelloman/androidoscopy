package com.lelloman.androidoscopy.session

enum class SessionMode { AUTO, MANUAL }

data class PairingRequest(val id: String, val code: String, val address: String)

data class SessionState(
    val active: Boolean = false,
    val sessionId: String? = null,
    val address: String? = null,
    val peer: String? = null,
    val pairing: PairingRequest? = null,
    val remainingMs: Long? = null,
    val reason: String? = null,
    val acceptAll: Boolean = false,
)

internal fun SessionState.withAcceptAll(enabled: Boolean, foreground: Boolean): SessionState {
    check(active) { "Start a diagnostic session first" }
    check(!enabled || foreground) { "Enable automatic pairing from a foreground Activity" }
    return copy(acceptAll = enabled, pairing = if (enabled) null else pairing)
}

internal enum class PairingDecision { MANUAL, AUTOMATIC, REJECTED }

internal fun shouldRememberPeer(debug: Boolean, decision: PairingDecision) = debug && decision == PairingDecision.MANUAL

internal fun SessionState.withConnectedPeer(remote: String) = copy(peer = remote, pairing = null, reason = null)

internal fun connectionEndedReason(error: Exception): String = when (error) {
    is java.io.EOFException, is java.net.SocketException -> "PC disconnected. Waiting for a connection."
    is kotlinx.coroutines.TimeoutCancellationException -> "Pairing request expired. Try connecting again."
    else -> when (error.message) {
        "PAIRING_REJECTED" -> "Pairing declined. Waiting for a connection."
        "PAIRING_RATE_LIMITED" -> "Too many pairing attempts. Wait a few seconds and try again."
        else -> "Connection failed. Try connecting again."
    }
}
