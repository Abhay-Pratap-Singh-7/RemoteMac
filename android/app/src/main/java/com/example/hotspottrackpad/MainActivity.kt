package com.example.hotspottrackpad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private lateinit var client: TrackpadClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = TrackpadClient(applicationContext)

        setContent {
            val status by client.status.collectAsState()

            when (val currentStatus = status) {
                is ConnectionStatus.Connected -> {
                    TrackpadScreen(
                        client = client,
                        connectedIp = currentStatus.ip,
                        onDisconnect = { client.disconnect() }
                    )
                }
                else -> {
                    ConnectScreen(client = client)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.close()
    }
}
