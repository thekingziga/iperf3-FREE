package dev.ziga.iperf3

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

/** Which transport a result was measured over, recorded alongside each run. */
object NetworkLabel {
    fun current(context: Context): String? {
        val manager = context.getSystemService<ConnectivityManager>() ?: return null
        val capabilities = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) } ?: return null
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> null
        }
    }
}
