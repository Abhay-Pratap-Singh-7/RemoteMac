package com.example.hotspottrackpad

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    data class Connecting(val message: String) : ConnectionStatus()
    data class Connected(val ip: String) : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

class TrackpadClient(private val context: Context, private val port: Int = 8080) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sendChannel = Channel<String>(capacity = Channel.UNLIMITED)
    private var socket: DatagramSocket? = null

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status

    private var targetIp: String? = null

    init {
        scope.launch {
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                }
                launch { processOutgoingQueue() }
            } catch (e: Exception) {
                _status.value = ConnectionStatus.Error("Failed to init UDP socket: ${e.localizedMessage}")
            }
        }
    }

    fun autoDiscover() {
        scope.launch {
            _status.value = ConnectionStatus.Connecting("Broadcasting on network...")
            var lock: WifiManager.MulticastLock? = null
            try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                lock = wifi?.createMulticastLock("trackpad_multicast")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }

                val socket = socket ?: DatagramSocket().also { this@TrackpadClient.socket = it }
                val sendData = "DISCOVER_SERVER".toByteArray()
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddr, port)

                socket.send(sendPacket)

                val buffer = ByteArray(1024)
                val receivePacket = DatagramPacket(buffer, buffer.size)
                socket.soTimeout = 2500
                socket.receive(receivePacket)

                val reply = String(receivePacket.data, 0, receivePacket.length)
                if (reply.contains("CONNECTED")) {
                    val foundIp = receivePacket.address.hostAddress ?: ""
                    targetIp = foundIp
                    _status.value = ConnectionStatus.Connected(foundIp)
                } else {
                    _status.value = ConnectionStatus.Error("Invalid response from server: $reply")
                }
            } catch (e: SocketTimeoutException) {
                _status.value = ConnectionStatus.Error("No Mac discovered via broadcast. Enter Mac IP manually.")
            } catch (e: Exception) {
                _status.value = ConnectionStatus.Error("Discovery error: ${e.localizedMessage}")
            } finally {
                try {
                    socket?.soTimeout = 0
                } catch (_: Exception) {}
                try {
                    lock?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun connectTo(ip: String) {
        val trimmedIp = ip.trim()
        if (trimmedIp.isEmpty()) {
            _status.value = ConnectionStatus.Error("Please enter a valid IP address")
            return
        }

        scope.launch {
            _status.value = ConnectionStatus.Connecting("Connecting to $trimmedIp...")
            try {
                val socket = socket ?: DatagramSocket().also { this@TrackpadClient.socket = it }
                val address = InetAddress.getByName(trimmedIp)
                val sendData = "CONNECT".toByteArray()
                val sendPacket = DatagramPacket(sendData, sendData.size, address, port)

                socket.send(sendPacket)

                val buffer = ByteArray(1024)
                val receivePacket = DatagramPacket(buffer, buffer.size)
                socket.soTimeout = 3000
                socket.receive(receivePacket)

                val reply = String(receivePacket.data, 0, receivePacket.length)
                if (reply.contains("CONNECTED")) {
                    targetIp = trimmedIp
                    _status.value = ConnectionStatus.Connected(trimmedIp)
                } else {
                    _status.value = ConnectionStatus.Error("Unexpected response: $reply")
                }
            } catch (e: SocketTimeoutException) {
                _status.value = ConnectionStatus.Error("Connection timed out. Check that MacTrackpadServer is running on $trimmedIp.")
            } catch (e: Exception) {
                _status.value = ConnectionStatus.Error("Connection failed: ${e.localizedMessage}")
            } finally {
                try {
                    socket?.soTimeout = 0
                } catch (_: Exception) {}
            }
        }
    }

    fun disconnect() {
        targetIp = null
        _status.value = ConnectionStatus.Disconnected
    }

    fun send(message: String) {
        sendChannel.trySend(message)
    }

    private suspend fun processOutgoingQueue() {
        for (message in sendChannel) {
            val ip = targetIp ?: continue
            try {
                val address = InetAddress.getByName(ip)
                val bytes = message.toByteArray()
                val packet = DatagramPacket(bytes, bytes.size, address, port)
                socket?.send(packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun close() {
        sendChannel.close()
        socket?.close()
        scope.cancel()
    }
}
