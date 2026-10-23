package com.banlimusic.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Small helper used to honour the "mobile network playback" preference. */
class NetworkMonitor(private val context: Context) {
    fun isMetered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
