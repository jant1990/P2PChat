package com.example.p2pchat

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class ChatServer(
    private val port: Int = 8988,
    private val onMessage: (ChatMessage) -> Unit
) {
    private var serverJob: Job? = null
    private val clients = CopyOnWriteArrayList<Socket>()

    fun start() {
        if (serverJob != null) return
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            ServerSocket(port).use { server ->
                while (isActive) {
                    val socket = server.accept()
                    clients += socket
                    launch { handleClient(socket) }
                }
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = coroutineScope {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        while (isActive) {
            val line = reader.readLine() ?: break
            val msg = parse(line)
            onMessage(msg)
            broadcast(line, except = null) // relay to all
        }
        clients.remove(socket)
        socket.close()
    }

    fun send(msg: ChatMessage) {
        broadcast(serialize(msg), except = null)
    }

    private fun broadcast(text: String, except: Socket?) {
        val dead = mutableListOf<Socket>()
        for (c in clients) {
            try {
                if (c != except) {
                    val out = PrintWriter(c.getOutputStream(), true)
                    out.println(text)
                }
            } catch (_: Exception) {
                dead += c
            }
        }
        clients.removeAll(dead.toSet())
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        clients.forEach { try { it.close() } catch (_: Exception) {} }
        clients.clear()
    }

    companion object {
        fun serialize(msg: ChatMessage): String =
            listOf("0", msg.sender, msg.text, msg.timestamp.toString(), msg.system.toString()).joinToString("|")

        fun parse(line: String): ChatMessage {
            val parts = line.split("|")
            return ChatMessage(
                sender = parts.getOrNull(1) ?: "unknown",
                text = parts.getOrNull(2) ?: "",
                timestamp = parts.getOrNull(3)?.toLongOrNull() ?: System.currentTimeMillis(),
                system = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: false
            )
        }
    }
}
