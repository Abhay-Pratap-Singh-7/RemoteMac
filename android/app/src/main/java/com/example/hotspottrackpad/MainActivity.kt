package com.example.hotspottrackpad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private lateinit var client: TrackpadClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = TrackpadClient(applicationContext)

        setContent {
            TrackpadScreen(client = client)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.close()
    }
}
