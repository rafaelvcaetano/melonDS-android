package me.magnum.melonds.impl.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(DelicateCoroutinesApi::class)
class NetworkConnectivityObserver @Inject constructor(@ApplicationContext context: Context) {

    enum class NetworkState {
        CONNECTED,
        DISCONNECTED,
    }

    val networkState: Flow<NetworkState> by lazy {
        flow {
            val connectivityManager = context.getSystemService<ConnectivityManager>() ?: return@flow
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val activeNetworkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (activeNetworkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
                    emit(NetworkState.CONNECTED)
                } else {
                    emit(NetworkState.DISCONNECTED)
                }
            } else {
                emit(NetworkState.DISCONNECTED)
            }

            callbackFlow<NetworkState> {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        val hasInternetConnection = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        if (hasInternetConnection) {
                            trySend(NetworkState.CONNECTED)
                        } else {
                            trySend(NetworkState.DISCONNECTED)
                        }
                    }
                }
                connectivityManager.registerDefaultNetworkCallback(callback)

                awaitClose {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            }.collect(this)
        }.distinctUntilChanged().shareIn(GlobalScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000, replayExpirationMillis = 0), replay = 1)
    }
}