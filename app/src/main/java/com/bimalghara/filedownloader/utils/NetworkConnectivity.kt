package com.bimalghara.filedownloader.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * Created by BimalGhara
 */

class NetworkConnectivityImpl @Inject constructor(val context: Context) : NetworkConnectivity {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override suspend fun getStatus(ioDispatcher: CoroutineContext): NetworkConnectivity.Status = withContext(ioDispatcher) {
        val network = connectivityManager.activeNetwork ?: return@withContext NetworkConnectivity.Status.Unavailable
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return@withContext NetworkConnectivity.Status.Unavailable
        return@withContext when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkConnectivity.Status.Available
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkConnectivity.Status.Available
            else -> NetworkConnectivity.Status.Unavailable
        }
    }

    override fun observe(): Flow<NetworkConnectivity.Status>  {
        return callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)

                    val activeNetwork = connectivityManager.getNetworkCapabilities(network)
                    activeNetwork?.let{
                        when {
                            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                                launch { send(NetworkConnectivity.Status.WIFI) }
                            }
                            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                                launch { send(NetworkConnectivity.Status.CELLULAR) }
                            }
                            else -> {
                                launch { send(NetworkConnectivity.Status.Unavailable) }
                            }
                        }
                    }
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    super.onLosing(network, maxMsToLive)
                    launch { send(NetworkConnectivity.Status.Losing) }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    launch { send(NetworkConnectivity.Status.Lost) }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    launch { send(NetworkConnectivity.Status.Unavailable) }
                }
            }

            connectivityManager.registerDefaultNetworkCallback(callback)
            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.distinctUntilChanged()
    }
}

interface NetworkConnectivity {

    suspend fun getStatus(ioDispatcher: CoroutineContext): Status

    fun observe(): Flow<Status>

    enum class Status {
        Available, WIFI, CELLULAR, Unavailable, Losing, Lost
    }
}
