package com.example.hotspottrackpad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun TrackpadScreen(
    client: TrackpadClient,
    connectedIp: String,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Top Connected Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF00E676), shape = RoundedCornerShape(5.dp))
                )
                Text(
                    text = "Connected: $connectedIp",
                    color = Color(0xFF00E676),
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Disconnect", fontSize = 12.sp, color = Color.White)
            }
        }

        // Main Trackpad Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
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
                                    client.send("MOVE,${change.x * 1.6f},${change.y * 1.6f}")
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
                text = "Touch Surface\n\n• 1 Finger: Move Cursor\n• 1 Finger Tap: Left Click\n• 2 Finger Drag: Scroll\n• 2 Finger Tap: Right Click",
                color = Color(0xFF666666),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Bottom Left & Right Click Action Bar
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
                    .height(54.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C))
            ) {
                Text("Left Click", color = Color.White)
            }

            Button(
                onClick = { client.send("RCLICK") },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C))
            ) {
                Text("Right Click", color = Color.White)
            }
        }
    }
}
