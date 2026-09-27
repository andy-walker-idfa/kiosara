package io.github.andy_walker_idfa.smarthome_dashboard.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/** Wi-Fi signal and IP address of the default network. No SSID: reading it needs location permission. */
data class WifiDetails(val rssiDbm: Int? = null, val ipAddress: String? = null)

interface WifiStatus {
    val details: StateFlow<WifiDetails>
}

class ConnectivityWifiStatus(context: Context, scope: CoroutineScope) : WifiStatus {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

    override val details: StateFlow<WifiDetails> =
        callbackFlow {
            var capabilities: NetworkCapabilities? = null
            var linkProperties: LinkProperties? = null
            fun emit() = trySend(WifiDetails(rssi(capabilities), ipv4(linkProperties)))
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        capabilities = caps
                        emit()
                    }

                    override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                        linkProperties = lp
                        emit()
                    }

                    override fun onLost(network: Network) {
                        capabilities = null
                        linkProperties = null
                        emit()
                    }
                }
            connectivityManager.registerDefaultNetworkCallback(callback)
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WifiDetails())

    private fun rssi(caps: NetworkCapabilities?): Int? {
        if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val value =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (caps.transportInfo as? WifiInfo)?.rssi
            } else {
                // API 29-30: needs ACCESS_WIFI_STATE (declared with maxSdkVersion 30).
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo?.rssi
            }
        // Android reports -127 (or Int.MIN_VALUE) when the signal is unknown.
        return value?.takeIf { it in MIN_VALID_RSSI..0 }
    }

    private fun ipv4(lp: LinkProperties?): String? = lp?.linkAddresses
        ?.map { it.address }
        ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        ?.hostAddress

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MIN_VALID_RSSI = -126
    }
}
