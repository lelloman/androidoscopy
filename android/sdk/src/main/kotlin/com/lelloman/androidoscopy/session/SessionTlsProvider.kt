package com.lelloman.androidoscopy.session

import android.annotation.TargetApi
import android.net.ssl.SSLSockets
import android.os.Build
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** TLS context and exporter must come from the same backend. Used by the optional tls-compat module. */
interface SessionTlsProvider {
    fun createContext(): SSLContext
    fun exportKeyingMaterial(socket: SSLSocket, label: String, context: ByteArray?, length: Int): ByteArray
}

internal const val UNSUPPORTED_SESSION_TLS = "Diagnostic sessions require Android 12 or newer on this device."

internal fun selectSessionTlsProvider(
    sdkInt: Int,
    legacyProvider: SessionTlsProvider?,
): SessionTlsProvider? = if (sdkInt >= Build.VERSION_CODES.S) PlatformSessionTlsProvider else legacyProvider

// Selected only on API 31+, keeping newer platform calls isolated from older runtimes.
@TargetApi(Build.VERSION_CODES.S)
internal object PlatformSessionTlsProvider : SessionTlsProvider {
    override fun createContext(): SSLContext = SSLContext.getInstance("TLS")

    override fun exportKeyingMaterial(socket: SSLSocket, label: String, context: ByteArray?, length: Int): ByteArray =
        requireNotNull(SSLSockets.exportKeyingMaterial(socket, label, context, length))
}
