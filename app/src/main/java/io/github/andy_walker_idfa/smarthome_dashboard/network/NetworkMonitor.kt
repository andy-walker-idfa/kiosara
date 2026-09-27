package io.github.andy_walker_idfa.smarthome_dashboard.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/** Reports whether the device currently has a usable network. */
interface NetworkMonitor {
    val isConnected: StateFlow<Boolean>
}

/**
 * "Connected" means the default network has [NetworkCapabilities.NET_CAPABILITY_INTERNET]. It
 * deliberately does not require VALIDATED: HA is on the LAN and must keep working when the internet
 * uplink is down (Android then marks Wi-Fi as unvalidated).
 */
class ConnectivityNetworkMonitor(context: Context, scope: CoroutineScope) : NetworkMonitor {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override val isConnected: StateFlow<Boolean> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                    }

                    override fun onLost(network: Network) {
                        trySend(false)
                    }
                }
            connectivityManager.registerDefaultNetworkCallback(callback)
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, initialValue = currentlyConnected())

    // Seeds the flow synchronously so start-up does not look like a false -> true reconnect.
    private fun currentlyConnected(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        AppLog.i(TAG, "Initial connectivity: $connected")
        return connected
    }

    private companion object {
        const val TAG = "Network"
    }
}
