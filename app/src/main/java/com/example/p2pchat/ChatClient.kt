package com.example.p2pchat

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket

class ChatClient(
    private val host: InetAddress,
    private val port: Int = 8988,
    private val onMessage: (ChatMessage) -> Unit
) {
    private var job: Job? = null
    private var out: PrintWriter? = null
    fun connect(onConnected: (Boolean) -> Unit = {}) {
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket(host, port)
                out = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                withContext(Dispatchers.Main) { onConnected(true) }
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val msg = ChatServer.parse(line)
                    onMessage(msg)
                }
                socket.close()
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onConnected(false) }
            }
        }
    }

    fun send(msg: ChatMessage) {
        out?.println(ChatServer.serialize(msg))
    }

    fun close() {
        job?.cancel()
        job = null
        out = null
    }
}
