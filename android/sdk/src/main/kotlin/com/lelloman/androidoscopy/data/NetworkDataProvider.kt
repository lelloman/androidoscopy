package com.lelloman.androidoscopy.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Data provider that collects network connectivity information.
 *
 * Collects:
 * - Connection type (WIFI, CELLULAR, ETHERNET, BLUETOOTH, VPN, NONE)
 * - Connection status (connected/disconnected)
 * - Metered network status
 * - WiFi signal strength (when connected via WiFi)
 * - Bandwidth information (API 29+)
 *
 * ## Required Permissions
 *
 * Add these permissions to your AndroidManifest.xml:
 * ```xml
 * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
 * ```
 *
 * These are normal permissions and do not require runtime approval.
 */
class NetworkDataProvider(
    private val context: Context,
    override val interval: Duration = 5.seconds
) : DataProvider {

    override val key = "network"

    override suspend fun collect(): Map<String, Any> {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val isConnected = capabilities != null
        val connectionType = getConnectionType(capabilities)
        val isMetered = connectivityManager.isActiveNetworkMetered

        val result = mutableMapOf<String, Any>(
            "is_connected" to isConnected,
            "connection_type" to connectionType,
            "is_metered" to isMetered
        )

        // Add WiFi-specific info if connected via WiFi
        if (connectionType == "WIFI") {
            val wifiInfo = getWifiInfo()
            result.putAll(wifiInfo)
        }

        // Add network capabilities info
        if (capabilities != null) {
            result["has_internet"] = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            result["has_validated"] = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                result["download_bandwidth_kbps"] = capabilities.linkDownstreamBandwidthKbps
                result["upload_bandwidth_kbps"] = capabilities.linkUpstreamBandwidthKbps
            }
        }

        return result
    }

    private fun getConnectionType(capabilities: NetworkCapabilities?): String {
        if (capabilities == null) return "NONE"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "OTHER"
        }
    }

    @Suppress("DEPRECATION")
    private fun getWifiInfo(): Map<String, Any> {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return emptyMap()

        val wifiInfo = wifiManager.connectionInfo ?: return emptyMap()

        val result = mutableMapOf<String, Any>()

        // Signal strength (0-4 levels, or raw RSSI)
        val rssi = wifiInfo.rssi
        if (rssi != -127) { // -127 means no signal info
            result["wifi_rssi"] = rssi
            result["wifi_signal_level"] = WifiManager.calculateSignalLevel(rssi, 5)
        }

        // Link speed in Mbps
        val linkSpeed = wifiInfo.linkSpeed
        if (linkSpeed != -1) {
            result["wifi_link_speed_mbps"] = linkSpeed
        }

        // Frequency (2.4GHz or 5GHz band)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val frequency = wifiInfo.frequency
            if (frequency > 0) {
                result["wifi_frequency_mhz"] = frequency
                result["wifi_band"] = if (frequency < 3000) "2.4GHz" else "5GHz"
            }
        }

        return result
    }
}
