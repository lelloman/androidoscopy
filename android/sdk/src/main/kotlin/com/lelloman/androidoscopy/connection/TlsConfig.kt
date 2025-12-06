package com.lelloman.androidoscopy.connection

import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * TLS configuration that trusts any server certificate.
 *
 * This allows the SDK to connect to servers using self-signed certificates
 * without requiring the Android app to enable cleartext traffic.
 */
internal object TlsConfig {
    private val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), java.security.SecureRandom())
    }

    fun configureClient(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        return builder
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
    }
}
