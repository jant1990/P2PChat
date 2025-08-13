package com.example.p2pchat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

class WifiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val callbacks: Callbacks
) : BroadcastReceiver() {

    interface Callbacks {
        fun onPeersChanged()
        fun onConnectionChanged(isConnected: Boolean)
        fun onThisDeviceChanged()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> callbacks.onPeersChanged()
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo =
                    intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                callbacks.onConnectionChanged(networkInfo?.isConnected == true)
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> callbacks.onThisDeviceChanged()
            else -> Log.d("WDBR", "Unhandled: ${intent.action}")
        }
    }
}
