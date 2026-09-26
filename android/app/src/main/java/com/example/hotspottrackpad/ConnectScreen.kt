package com.example.hotspottrackpad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
            .background(Color(0xFF121212))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "📱 Hotspot Trackpad",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Connect to your Mac before opening the trackpad",
                    fontSize = 13.sp,
                    color = Color(0xFFAAAAAA),
                    textAlign = TextAlign.Center
                )

                // Status Indicator
                when (val current = status) {
                    is ConnectionStatus.Connecting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF64B5F6)
                            )
                            Text(
                                text = current.message,
                                color = Color(0xFF64B5F6),
                                fontSize = 13.sp
                            )
                        }
                    }
                    is ConnectionStatus.Error -> {
                        Text(
                            text = current.message,
                            color = Color(0xFFEF5350),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {}
                }

                HorizontalDivider(color = Color(0xFF2C2C2C))

                // Auto Discover Button
                Button(
                    onClick = { client.autoDiscover() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2962FF))
                ) {
                    Text("🔍 Auto-Discover Mac", color = Color.White)
                }

                Text(
                    text = "— OR ENTER MAC IP MANUALLY —",
                    fontSize = 11.sp,
                    color = Color(0xFF777777),
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = inputIp,
                    onValueChange = { inputIp = it },
                    label = { Text("Mac IP Address", color = Color(0xFFAAAAAA)) },
                    placeholder = { Text("e.g. 192.168.43.15 or 10.x.x.x", color = Color(0xFF666666)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2962FF),
                        unfocusedBorderColor = Color(0xFF444444)
                    )
                )

                Button(
                    onClick = { client.connectTo(inputIp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) {
                    Text("Connect to Mac", color = Color.White)
                }

                Text(
                    text = "Tip: Run MacTrackpadServer in Terminal on your Mac to view your Mac's active IP address.",
                    fontSize = 11.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
