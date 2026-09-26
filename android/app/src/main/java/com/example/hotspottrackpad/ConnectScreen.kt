package com.example.hotspottrackpad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ConnectMode {
    LOCAL_WIFI,
    ONLINE_CLOUD
}

@Composable
fun ConnectScreen(client: TrackpadClient) {
    val status by client.status.collectAsState()
    var mode by remember { mutableStateOf(ConnectMode.LOCAL_WIFI) }
    var inputIp by remember { mutableStateOf("10.202.35.108") }
    var relayUrl by remember { mutableStateOf("wss://hotspot-trackpad-relay.onrender.com") }
    var roomCode by remember { mutableStateOf("123456") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .verticalScroll(rememberScrollState()),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📱 Hotspot Trackpad & Online Controller",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Mode Selector Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF121212), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (mode == ConnectMode.LOCAL_WIFI) Color(0xFF2962FF) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { mode = ConnectMode.LOCAL_WIFI }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📶 Local Wi-Fi (UDP)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (mode == ConnectMode.LOCAL_WIFI) Color.White else Color(0xFFAAAAAA)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (mode == ConnectMode.ONLINE_CLOUD) Color(0xFF00C853) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { mode = ConnectMode.ONLINE_CLOUD }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🌐 Online Cloud (Render)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (mode == ConnectMode.ONLINE_CLOUD) Color.White else Color(0xFFAAAAAA)
                        )
                    }
                }

                // Status Indicator
                when (val current = status) {
                    is ConnectionStatus.Connecting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF64B5F6)
                            )
                            Text(
                                text = current.message,
                                color = Color(0xFF64B5F6),
                                fontSize = 12.sp
                            )
                        }
                    }
                    is ConnectionStatus.Error -> {
                        Text(
                            text = current.message,
                            color = Color(0xFFEF5350),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {}
                }

                if (mode == ConnectMode.LOCAL_WIFI) {
                    // Local Network Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputIp,
                            onValueChange = { inputIp = it },
                            label = { Text("Mac Local IP", color = Color(0xFFAAAAAA), fontSize = 11.sp) },
                            placeholder = { Text("e.g. 10.202.35.108", color = Color(0xFF666666)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF2962FF),
                                unfocusedBorderColor = Color(0xFF444444)
                            )
                        )

                        Button(
                            onClick = { client.connectTo(inputIp) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            Text("Connect", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { client.autoDiscover() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2962FF)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            Text("Auto-Scan", color = Color.White)
                        }
                    }

                    Text(
                        text = "Connects directly via fast UDP. Both devices must be on the same Wi-Fi or Mobile Hotspot.",
                        fontSize = 10.sp,
                        color = Color(0xFF777777),
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Online Cloud Relay Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = relayUrl,
                            onValueChange = { relayUrl = it },
                            label = { Text("Render Relay URL", color = Color(0xFFAAAAAA), fontSize = 11.sp) },
                            placeholder = { Text("wss://your-relay.onrender.com", color = Color(0xFF666666)) },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00C853),
                                unfocusedBorderColor = Color(0xFF444444)
                            )
                        )

                        OutlinedTextField(
                            value = roomCode,
                            onValueChange = { roomCode = it },
                            label = { Text("Room PIN", color = Color(0xFFAAAAAA), fontSize = 11.sp) },
                            placeholder = { Text("123456", color = Color(0xFF666666)) },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00C853),
                                unfocusedBorderColor = Color(0xFF444444)
                            )
                        )

                        Button(
                            onClick = { client.connectRelay(relayUrl, roomCode) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text("Connect", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "🌐 Connects anywhere over the internet (cellular data, different Wi-Fi networks) via Render WebSocket relay.",
                        fontSize = 10.sp,
                        color = Color(0xFF81C784),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
