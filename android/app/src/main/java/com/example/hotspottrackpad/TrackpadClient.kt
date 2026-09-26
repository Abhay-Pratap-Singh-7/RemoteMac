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

class TrackpadClient(private val context: Context, private val port: Int = 8080) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sendChannel = Channel<String>(capacity = Channel.UNLIMITED)
    private var socket: DatagramSocket? = null

    private val _connectedIp = MutableStateFlow<String?>(null)
    val connectedIp: StateFlow<String?> = _connectedIp

    init {
        scope.launch {
            try {
                socket = DatagramSocket().apply { broadcast = true }
                launch { processOutgoingQueue() }
                autoDiscover()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setServerIp(ip: String) {
        _connectedIp.value = ip
    }

    fun autoDiscover() {
        scope.launch {
            try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val lock = wifi?.createMulticastLock("trackpad_lock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }

                val discoverBytes = "DISCOVER_SERVER".toByteArray()
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val sendPacket = DatagramPacket(discoverBytes, discoverBytes.size, broadcastAddr, port)

                socket?.send(sendPacket)

                val buffer = ByteArray(1024)
                val receivePacket = DatagramPacket(buffer, buffer.size)
                socket?.soTimeout = 2500
                socket?.receive(receivePacket)

                val response = String(receivePacket.data, 0, receivePacket.length)
                if (response.contains("SERVER_HERE")) {
                    val host = receivePacket.address.hostAddress
                    _connectedIp.value = host
                }
                lock?.release()
            } catch (e: Exception) {
                // If broadcast discovery fails, default to typical Hotspot host gateway IP
                if (_connectedIp.value == null) {
                    _connectedIp.value = "192.168.43.1"
                }
            } finally {
                try {
                    socket?.soTimeout = 0
                } catch (_: Exception) {}
            }
        }
    }

    fun send(message: String) {
        sendChannel.trySend(message)
    }

    private suspend fun processOutgoingQueue() {
        for (message in sendChannel) {
            val ipStr = _connectedIp.value ?: continue
            try {
                val address = InetAddress.getByName(ipStr)
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
