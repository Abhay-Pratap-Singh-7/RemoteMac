package com.example.hotspottrackpad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun TrackpadScreen(client: TrackpadClient) {
    val serverIp by client.connectedIp.collectAsState()
    var manualIp by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Status Bar / IP bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Target Mac: ${serverIp ?: "Searching..."}",
                color = if (serverIp != null) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                fontSize = 13.sp
            )

            Button(
                onClick = { client.autoDiscover() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Retry", fontSize = 12.sp, color = Color.White)
            }
        }

        // Trackpad Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .background(Color(0xFF1E1E1E))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val startTime = System.currentTimeMillis()
                        var totalMovement = 0f
                        var maxFingerCount = 1

                        while (true) {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }
                            if (pointers.isEmpty()) break

                            if (pointers.size > maxFingerCount) {
                                maxFingerCount = pointers.size
                            }

                            if (pointers.size == 1) {
                                val change = pointers[0].positionChange()
                                totalMovement += abs(change.x) + abs(change.y)
                                if (change.x != 0f || change.y != 0f) {
                                    client.send("MOVE,${change.x * 1.5f},${change.y * 1.5f}")
                                    pointers[0].consume()
                                }
                            } else if (pointers.size >= 2) {
                                val p1 = pointers[0].positionChange()
                                val p2 = pointers[1].positionChange()
                                val avgDx = (p1.x + p2.x) / 2f
                                val avgDy = (p1.y + p2.y) / 2f
                                totalMovement += abs(avgDx) + abs(avgDy)
                                if (avgDx != 0f || avgDy != 0f) {
                                    client.send("SCROLL,${(-avgDx).toInt()},${avgDy.toInt()}")
                                    pointers[0].consume()
                                    pointers[1].consume()
                                }
                            }
                        }

                        val duration = System.currentTimeMillis() - startTime
                        if (duration < 250 && totalMovement < 15f) {
                            if (maxFingerCount >= 2) {
                                client.send("RCLICK")
                            } else {
                                client.send("CLICK")
                            }
                        }
                    }
                }
        ) {
            Text(
                text = "Touch Surface\n1 Finger: Move & Tap to Click\n2 Fingers: Scroll & Tap for Right-Click",
                color = Color(0xFF555555),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Bottom Left/Right Click Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { client.send("CLICK") },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C))
            ) {
                Text("Left Click", color = Color.White)
            }
            Button(
                onClick = { client.send("RCLICK") },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C))
            ) {
                Text("Right Click", color = Color.White)
            }
        }
    }
}
