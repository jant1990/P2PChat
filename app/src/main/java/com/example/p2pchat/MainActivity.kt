package com.example.p2pchat

import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var wdm: WifiDirectManager
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wdm = WifiDirectManager(this)
        lifecycle.addObserver(wdm)
        setContent {
            AppScreen(
                vm = vm,
                wdm = wdm
            )
        }
        vm.bindWifi(wdm)
    }
}

class ChatViewModel : ViewModel() {
    private var wdm: WifiDirectManager? = null
    val messages = mutableStateListOf<ChatMessage>()
    val nickname = mutableStateOf("")
    private var server: ChatServer? = null
    private var client: ChatClient? = null

    fun bindWifi(manager: WifiDirectManager) {
        wdm = manager
    }

    fun addMessage(msg: ChatMessage) {
        messages += msg
    }

    fun startAsGroupOwner() {
        wdm?.createGroup {
            if (it) {
                // group owner acts as server
                server = ChatServer { m -> addMessage(m) }.also { s -> s.start() }
                addMessage(ChatMessage("SYSTEM", "Grupo creado. Esperando clientes…", system = true))
            } else addMessage(ChatMessage("SYSTEM", "Fallo al crear el grupo", system = true))
        }
    }

    fun connectToOwner(device: WifiP2pDevice) {
        wdm?.connect(device) { ok ->
            if (!ok) addMessage(ChatMessage("SYSTEM", "Fallo al conectar", system = true))
            else {
                wdm?.requestConnectionInfo { info ->
                    if (info.groupFormed && !info.isGroupOwner) {
                        client = ChatClient(info.groupOwnerAddress) { m -> addMessage(m) }.also { c ->
                            c.connect { connected ->
                                if (connected) addMessage(ChatMessage("SYSTEM", "Conectado al grupo", system = true))
                                else addMessage(ChatMessage("SYSTEM", "No se pudo conectar al socket", system = true))
                            }
                        }
                    }
                }
            }
        }
    }

    fun send(text: String) {
        val nn = nickname.value.ifBlank { "Anon" }
        val msg = ChatMessage(sender = nn, text = text)
        // send through server (broadcast) or client (to server)
        server?.send(msg)
        client?.send(msg)
        // also show locally
        addMessage(msg)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: ChatViewModel, wdm: WifiDirectManager) {
    val peers = wdm.peers
    val discovering by wdm.isDiscovering
    val msgs = vm.messages

    LaunchedEffect(Unit) {
        // Ask permissions at start
        wdm.requestPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("P2PChat (Wi‑Fi Direct)") })
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            OutlinedTextField(
                value = vm.nickname.value,
                onValueChange = { vm.nickname.value = it },
                label = { Text("Tu nombre") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { wdm.discoverPeers { } }, enabled = !discovering) {
                    Text(if (discovering) "Buscando…" else "Buscar dispositivos")
                }
                Button(onClick = { vm.startAsGroupOwner() }) {
                    Text("Crear grupo")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Dispositivos cercanos", fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                items(peers) { device ->
                    ElevatedCard(onClick = { vm.connectToOwner(device) }, modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(device.deviceName.ifBlank { device.deviceAddress })
                            Text(device.deviceAddress, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Chat", fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.weight(1f)) {
                items(msgs) { m ->
                    Text("[${m.prettyTime()}] ${m.sender}: ${m.text}")
                }
            }
            var input by remember { mutableStateOf("") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Mensaje") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { if (input.isNotBlank()) { vm.send(input); input = "" } }) {
                    Text("Enviar")
                }
            }
        }
    }
}
