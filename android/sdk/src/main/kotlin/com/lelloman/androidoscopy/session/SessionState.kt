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
)
