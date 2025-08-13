package com.example.p2pchat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WifiDirectManager(
    private val activity: Activity
) : DefaultLifecycleObserver, WifiP2pManager.ChannelListener, WifiDirectBroadcastReceiver.Callbacks {

    private val manager: WifiP2pManager =
        activity.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel =
        manager.initialize(activity, activity.mainLooper, this)

    private var receiver: WifiDirectBroadcastReceiver? = null
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    val peers = mutableStateListOf<WifiP2pDevice>()
    val connectionInfo = mutableStateOf<WifiP2pInfo?>(null)
    val isDiscovering = mutableStateOf(false)

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result ignored; we try ops anyway and surface errors in UI */ }

    fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    override fun onCreate(owner: LifecycleOwner) {
        receiver = WifiDirectBroadcastReceiver(manager, channel, this)
        activity.registerReceiver(receiver, intentFilter)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        try { activity.unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    fun discoverPeers(onResult: (Boolean) -> Unit) {
        isDiscovering.value = true
        manager.discoverPeers(channel) { success ->
            isDiscovering.value = false
            onResult(success)
            if (!success) Log.e("WDM", "discoverPeers failed")
        }
    }

    fun requestPeers() {
        manager.requestPeers(channel) { list ->
            peers.clear()
            peers.addAll(list.deviceList)
        }
    }

    fun connect(device: WifiP2pDevice, onResult: (Boolean) -> Unit) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = android.net.wifi.WpsInfo.PBC
        }
        manager.connect(channel, config) { onResult(it) }
    }

    fun createGroup(onResult: (Boolean) -> Unit) {
        manager.createGroup(channel) { onResult(it) }
    }

    fun removeGroup(onResult: (Boolean) -> Unit) {
        manager.removeGroup(channel) { onResult(it) }
    }

    fun requestConnectionInfo(onInfo: (WifiP2pInfo) -> Unit) {
        manager.requestConnectionInfo(channel) { info ->
            connectionInfo.value = info
            onInfo(info)
        }
    }

    override fun onPeersChanged() = requestPeers()

    override fun onConnectionChanged(isConnected: Boolean) {
        if (isConnected) {
            requestConnectionInfo { /* updated */ }
        } else {
            connectionInfo.value = null
        }
    }

    override fun onThisDeviceChanged() { /* no-op */ }

    override fun onChannelDisconnected() {
        Log.w("WDM", "Channel disconnected; reinitializing")
    }
}
