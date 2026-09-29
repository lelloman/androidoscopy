package com.lelloman.androidoscopy.session

import org.junit.Assert.*
import org.junit.Test
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class SessionTlsProviderTest {
    private val legacy = object : SessionTlsProvider {
        override fun createContext(): SSLContext = error("Selection must not initialize TLS")
        override fun exportKeyingMaterial(socket: SSLSocket, label: String, context: ByteArray?, length: Int): ByteArray =
            error("Selection must not initialize TLS")
    }

    @Test fun olderDevicesRequireExplicitCompatibilityProvider() {
        for (api in 24..30) {
            assertNull(selectSessionTlsProvider(api, null))
            assertSame(legacy, selectSessionTlsProvider(api, legacy))
        }
    }

    @Test fun modernDevicesAlwaysUsePlatformTls() {
        for (api in 31..37) {
            assertSame(PlatformSessionTlsProvider, selectSessionTlsProvider(api, null))
            assertSame(PlatformSessionTlsProvider, selectSessionTlsProvider(api, legacy))
        }
    }
}
