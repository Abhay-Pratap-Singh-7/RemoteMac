package com.example.hotspottrackpad

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    data class Connecting(val message: String) : ConnectionStatus()
    data class Connected(val ip: String) : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

data class MacTab(
    val id: Int,
    val appName: String,
    val title: String,
    val type: String
)

data class AiTaskResult(
    val success: Boolean,
    val summary: String,
    val command: String,
    val error: String?
)

class TrackpadClient(private val context: Context, private val port: Int = 8080) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sendChannel = Channel<String>(capacity = Channel.UNLIMITED)
    private var socket: DatagramSocket? = null
    private var webSocket: WebSocket? = null

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status

    private val _installedApps = MutableStateFlow<List<String>>(emptyList())
    val installedApps: StateFlow<List<String>> = _installedApps

    private val _openTabs = MutableStateFlow<List<MacTab>>(emptyList())
    val openTabs: StateFlow<List<MacTab>> = _openTabs

    private val _isAiKeyConfigured = MutableStateFlow(false)
    val isAiKeyConfigured: StateFlow<Boolean> = _isAiKeyConfigured

    private val _aiResult = MutableStateFlow<AiTaskResult?>(null)
    val aiResult: StateFlow<AiTaskResult?> = _aiResult

    private val _isAiRunning = MutableStateFlow(false)
    val isAiRunning: StateFlow<Boolean> = _isAiRunning

    private val _isRelayMode = MutableStateFlow(false)
    val isRelayMode: StateFlow<Boolean> = _isRelayMode

    private val _streamFrame = MutableStateFlow<ByteArray?>(null)
    val streamFrame: StateFlow<ByteArray?> = _streamFrame

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
        if (message.startsWith("CONNECTED")) {
            val ip = fromAddress.hostAddress ?: targetIp ?: return
            targetIp = ip
            _status.value = ConnectionStatus.Connected(ip)
            if (message.contains("KEY_SET")) {
                _isAiKeyConfigured.value = true
            } else if (message.contains("NO_KEY")) {
                _isAiKeyConfigured.value = false
            }
            checkAiKey()
            fetchApps()
            fetchOpenTabs()
        } else if (message.startsWith("AI_KEY_STATUS,")) {
            val status = message.removePrefix("AI_KEY_STATUS,").trim()
            _isAiKeyConfigured.value = (status == "CONFIGURED")
        } else if (message.startsWith("AI_RESULT,")) {
            _isAiRunning.value = false
            val parts = message.split(",")
            val status = parts.getOrNull(1) ?: ""
            val summary = parts.getOrNull(2)?.replace(";", ",") ?: ""
            val cmd = parts.getOrNull(3)?.replace(";", ",") ?: ""
            if (status == "SUCCESS") {
                _aiResult.value = AiTaskResult(true, summary, cmd, null)
                logToTerminal("AI task executed successfully: \"$summary\" -> $cmd")
            } else {
                val err = summary.ifEmpty { "AI execution error on Mac" }
                _aiResult.value = AiTaskResult(false, "", "", err)
                logToTerminal("AI task failed: $err")
            }
        } else if (message.startsWith("APPS:")) {
            val appNames = message.removePrefix("APPS:").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            _installedApps.value = appNames
        } else if (message.startsWith("TABS:")) {
            val raw = message.removePrefix("TABS:").trim()
            if (raw.isNotEmpty()) {
                val items = raw.split("###").mapNotNull { itemStr ->
                    val p = itemStr.split("|||")
                    if (p.size >= 4) {
                        val id = p[0].toIntOrNull() ?: return@mapNotNull null
                        MacTab(
                            id = id,
                            appName = p[1],
                            title = p[2],
                            type = p[3]
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
        send("SWITCH_TAB,${tab.id}")
    }

    fun launchApp(appName: String) {
        send("LAUNCH_APP,$appName")
    }

    fun sendAction(action: String) {
        send("ACTION,$action")
    }

    fun checkAiKey() {
        send("CHECK_AI_KEY")
    }

    fun setServerAiKey(key: String) {
        send("SET_AI_KEY,${key.trim()}")
    }

    fun logToTerminal(message: String) {
        android.util.Log.i("TrackpadAI", message)
        send("AI_LOG,$message")
    }

    fun sendAiTask(prompt: String) {
        _isAiRunning.value = true
        _aiResult.value = null
        logToTerminal("User requested AI prompt: \"$prompt\"")
        val b64 = Base64.encodeToString(prompt.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        send("AI_TASK_B64,$b64")
    }

    fun executeAiCommand(type: String, command: String) {
        val b64 = Base64.encodeToString(command.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        send("AI_EXEC,$type,$b64")
    }

    fun typeText(text: String) {
        if (text.isEmpty()) return
        val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        send("TYPE_B64,$b64")
    }

    fun sendKey(key: String) {
        send("KEY,$key")
    }

    fun connectRelay(relayUrl: String, room: String) {
        disconnect()
        _status.value = ConnectionStatus.Connecting("Connecting to Cloud Relay...")
        _isRelayMode.value = true

        val cleanUrl = relayUrl.trim().removeSuffix("/")
        val wsUrl = when {
            cleanUrl.startsWith("http://") -> cleanUrl.replaceFirst("http://", "ws://")
            cleanUrl.startsWith("https://") -> cleanUrl.replaceFirst("https://", "wss://")
            cleanUrl.startsWith("ws://") || cleanUrl.startsWith("wss://") -> cleanUrl
            else -> "wss://$cleanUrl"
        }
        val roomCode = room.trim().ifEmpty { "123456" }
        val fullUrl = "$wsUrl/?role=phone&room=$roomCode"

        val okHttpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(fullUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _status.value = ConnectionStatus.Connected("Cloud Relay ($roomCode)")
                send("CONNECT")
                fetchApps()
                fetchOpenTabs()
                checkAiKey()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text, InetAddress.getLoopbackAddress())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                _streamFrame.value = bytes.toByteArray()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _status.value = ConnectionStatus.Error("Relay error: ${t.localizedMessage ?: "Connection failed"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _status.value = ConnectionStatus.Disconnected
            }
        })
    }

    fun disconnect() {
        try { webSocket?.close(1000, "Disconnected by user") } catch (_: Exception) {}
        webSocket = null
        _isRelayMode.value = false
        _streamFrame.value = null
        targetIp = null
        _status.value = ConnectionStatus.Disconnected
        _installedApps.value = emptyList()
        _openTabs.value = emptyList()
    }

    fun send(message: String) {
        if (_isRelayMode.value) {
            webSocket?.send(message)
        } else {
            sendChannel.trySend(message)
        }
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
