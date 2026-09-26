package com.example.hotspottrackpad

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    data class Connecting(val message: String) : ConnectionStatus()
    data class Connected(val ip: String) : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

data class MacTab(
    val appName: String,
    val title: String,
    val type: String,
    val target: String
)

class TrackpadClient(private val context: Context, private val port: Int = 8080) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sendChannel = Channel<String>(capacity = Channel.UNLIMITED)
    private var socket: DatagramSocket? = null

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status

    private val _installedApps = MutableStateFlow<List<String>>(emptyList())
    val installedApps: StateFlow<List<String>> = _installedApps

    private val _openTabs = MutableStateFlow<List<MacTab>>(emptyList())
    val openTabs: StateFlow<List<MacTab>> = _openTabs

    private var targetIp: String? = null

    init {
        scope.launch {
            try {
                socket = DatagramSocket().apply { broadcast = true }
                launch { processOutgoingQueue() }
                launch { listenForIncomingPackets() }
            } catch (e: Exception) {
                _status.value = ConnectionStatus.Error("Failed to init UDP socket: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun listenForIncomingPackets() {
        val buffer = ByteArray(65535)
        while (scope.isActive) {
            try {
                val s = socket ?: break
                val packet = DatagramPacket(buffer, buffer.size)
                s.receive(packet)
                val reply = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                handleIncomingMessage(reply, packet.address)
            } catch (e: Exception) {
                if (!scope.isActive) break
            }
        }
    }

    private fun handleIncomingMessage(message: String, fromAddress: InetAddress) {
        if (message == "CONNECTED") {
            val ip = fromAddress.hostAddress ?: targetIp ?: return
            targetIp = ip
            _status.value = ConnectionStatus.Connected(ip)
            fetchApps()
            fetchOpenTabs()
        } else if (message.startsWith("APPS:")) {
            val appNames = message.removePrefix("APPS:").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            _installedApps.value = appNames
        } else if (message.startsWith("TABS:")) {
            val raw = message.removePrefix("TABS:").trim()
            if (raw.isNotEmpty()) {
                val items = raw.split("###").mapNotNull { itemStr ->
                    val p = itemStr.split("|||")
                    if (p.size >= 4) {
                        MacTab(
                            appName = p[0],
                            title = p[1],
                            type = p[2],
                            target = p.drop(2).joinToString("|||")
                        )
                    } else null
                }
                _openTabs.value = items
            } else {
                _openTabs.value = emptyList()
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

                val sendData = "DISCOVER_SERVER".toByteArray()
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(sendData, sendData.size, broadcastAddr, port)
                socket?.send(packet)

                withTimeout(3000) {
                    while (status.value !is ConnectionStatus.Connected) {
                        delay(100)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (status.value !is ConnectionStatus.Connected) {
                    _status.value = ConnectionStatus.Error("No Mac discovered via broadcast. Enter Mac IP manually.")
                }
            } catch (e: Exception) {
                _status.value = ConnectionStatus.Error("Discovery error: ${e.localizedMessage}")
            } finally {
                try { lock?.release() } catch (_: Exception) {}
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
                targetIp = trimmedIp
                val sendData = "CONNECT".toByteArray()
                val address = InetAddress.getByName(trimmedIp)
                val packet = DatagramPacket(sendData, sendData.size, address, port)
                socket?.send(packet)

                withTimeout(3500) {
                    while (status.value !is ConnectionStatus.Connected) {
                        delay(100)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (status.value !is ConnectionStatus.Connected) {
                    _status.value = ConnectionStatus.Error("Connection timed out. Check that MacTrackpadServer is running on $trimmedIp.")
                }
            } catch (e: Exception) {
                _status.value = ConnectionStatus.Error("Connection failed: ${e.localizedMessage}")
            }
        }
    }

    fun fetchApps() {
        send("GET_APPS")
    }

    fun fetchOpenTabs() {
        send("GET_TABS")
    }

    fun switchTab(tab: MacTab) {
        send("SWITCH_TAB,${tab.type},${tab.target}")
    }

    fun launchApp(appName: String) {
        send("LAUNCH_APP,$appName")
    }

    fun typeText(text: String) {
        if (text.isEmpty()) return
        val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        send("TYPE_B64,$b64")
    }

    fun sendKey(key: String) {
        send("KEY,$key")
    }

    fun disconnect() {
        targetIp = null
        _status.value = ConnectionStatus.Disconnected
        _installedApps.value = emptyList()
        _openTabs.value = emptyList()
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
