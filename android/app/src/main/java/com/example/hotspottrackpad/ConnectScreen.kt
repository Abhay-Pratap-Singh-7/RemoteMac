package com.example.hotspottrackpad

import androidx.compose.foundation.background
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

@Composable
fun ConnectScreen(client: TrackpadClient) {
    val status by client.status.collectAsState()
    var inputIp by remember { mutableStateOf("10.202.35.108") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .verticalScroll(rememberScrollState()),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "📱 Hotspot Trackpad & Stream",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputIp,
                        onValueChange = { inputIp = it },
                        label = { Text("Mac IP Address", color = Color(0xFFAAAAAA), fontSize = 11.sp) },
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
                    text = "Tip: Run MacTrackpadServer in Terminal on your Mac to view your Mac's active IP address.",
                    fontSize = 10.sp,
                    color = Color(0xFF777777),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
