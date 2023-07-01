package com.bimalghara.filedownloader.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import com.bimalghara.filedownloader.utils.Logger.logs
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

    override fun observe(): Flow<Pair<NetworkConnectivity.Status, Long>>  {
        return callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)

                    val activeNetwork = connectivityManager.getNetworkCapabilities(network)
                    activeNetwork?.let{
                        val uid = android.os.Process.myUid()
                        val speed = TrafficStats.getUidRxBytes(uid)
                        when {
                            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                                logs("NetworkConnectivityImpl", "Network speed on wifi: $speed/B")
                                launch { send(Pair(NetworkConnectivity.Status.WIFI, speed)) }
                            }
                            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                                logs("NetworkConnectivityImpl", "Network speed on cellular: $speed/B")
                                launch { send(Pair(NetworkConnectivity.Status.CELLULAR, speed)) }
                            }
                            else -> {
                                launch { send(Pair(NetworkConnectivity.Status.Unavailable, 0L)) }
                            }
                        }
                    }
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    super.onLosing(network, maxMsToLive)
                    launch { send(Pair(NetworkConnectivity.Status.Losing, 0L)) }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    launch { send(Pair(NetworkConnectivity.Status.Lost, 0L)) }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    launch { send(Pair(NetworkConnectivity.Status.Unavailable, 0L)) }
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

    fun observe(): Flow<Pair<Status, Long>>

    enum class Status {
        Available, WIFI, CELLULAR, Unavailable, Losing, Lost
    }
}
