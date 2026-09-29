package com.lelloman.androidoscopy.tlscompat

import com.lelloman.androidoscopy.session.SessionTlsProvider
import org.conscrypt.Conscrypt
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** Opt-in TLS 1.3 and exporter support for Android 7–11. Does not alter global security providers. */
class ConscryptSessionTlsProvider : SessionTlsProvider {
    private val provider by lazy { Conscrypt.newProvider() }

    override fun createContext(): SSLContext = SSLContext.getInstance("TLS", provider)

    override fun exportKeyingMaterial(socket: SSLSocket, label: String, context: ByteArray?, length: Int): ByteArray =
        requireNotNull(Conscrypt.exportKeyingMaterial(socket, label, context, length))
}
