package com.example.hotspottrackpad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

enum class ActiveScreen {
    TRACKPAD,
    TABS,
    APPS
}

@Composable
fun TrackpadScreen(
    client: TrackpadClient,
    connectedIp: String,
    onDisconnect: () -> Unit
) {
    var activeScreen by remember { mutableStateOf(ActiveScreen.TRACKPAD) }
    var isStreamEnabled by remember { mutableStateOf(false) }
    var showKeyboardBar by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    val snackbarHostState = remember { SnackbarHostState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        // Main Screen Area
        when (activeScreen) {
            ActiveScreen.TRACKPAD -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background Live Mac Screen Stream (if toggled on)
                    if (isStreamEnabled) {
                        MjpegStreamView(
                            serverIp = connectedIp,
                            port = 8081,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF141414)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🖱️ Trackpad Active\n1 Finger: Move & Tap  |  2 Fingers: Scroll & Right-Click\nTap 📺 to Toggle Mac Screen Stream",
                                color = Color(0xFF444444),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    // Transparent Touch Layer for Trackpad Gestures
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                    )

                    // Floating Left Click Button (Bottom Left)
                    FloatingActionButton(
                        onClick = { client.send("CLICK") },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 14.dp)
                            .size(52.dp),
                        shape = CircleShape,
                        containerColor = Color(0xCC262626),
                        contentColor = Color.White
                    ) {
                        Text("L", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    // Floating Right Click Button (Bottom Right)
                    FloatingActionButton(
                        onClick = { client.send("RCLICK") },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 14.dp)
                            .size(52.dp),
                        shape = CircleShape,
                        containerColor = Color(0xCC262626),
                        contentColor = Color.White
                    ) {
                        Text("R", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            ActiveScreen.TABS -> {
                TabsScreen(
                    client = client,
                    snackbarHostState = snackbarHostState
                )
            }

            ActiveScreen.APPS -> {
                AppsScreen(
                    client = client,
                    snackbarHostState = snackbarHostState
                )
            }
        }

        // Floating Real-Time Keyboard Input Bar (when enabled)
        if (showKeyboardBar) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp, start = 48.dp, end = 48.dp)
                    .fillMaxWidth(0.85f),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE1E1E1E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { newVal ->
                                val oldText = textValue.text
                                val newText = newVal.text
                                if (newText.length > oldText.length) {
                                    val added = newText.substring(oldText.length)
                                    client.typeText(added)
                                } else if (newText.length < oldText.length) {
                                    val count = oldText.length - newText.length
                                    repeat(count) { client.sendKey("BACKSPACE") }
                                }
                                textValue = newVal
                            },
                            placeholder = { Text("Live typing streams to Mac...", color = Color(0xFF777777), fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && textValue.text.isEmpty()) {
                                        client.sendKey("BACKSPACE")
                                        true
                                    } else false
                                },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { client.sendKey("ENTER") }),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E676),
                                unfocusedBorderColor = Color(0xFF444444)
                            )
                        )

                        Button(
                            onClick = { textValue = TextFieldValue("") },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Clear", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                        }

                        Button(
                            onClick = { showKeyboardBar = false },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("✕", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    // Quick action keys row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { client.sendKey("ENTER") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("↵ Enter", fontSize = 10.sp, color = Color.White)
                        }
                        Button(
                            onClick = { client.sendKey("BACKSPACE") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("⌫ Del", fontSize = 10.sp, color = Color.White)
                        }
                        Button(
                            onClick = { client.sendKey("SPACE") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("␣ Space", fontSize = 10.sp, color = Color.White)
                        }
                        Button(
                            onClick = { client.sendKey("TAB") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("⇥ Tab", fontSize = 10.sp, color = Color.White)
                        }
                        Button(
                            onClick = { client.sendKey("ESCAPE") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("⎋ Esc", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // Floating Bottom Navigation Pill (Dock)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xDD1C1C1C),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Trackpad Button
                FloatingDockButton(
                    label = "🖱️ Trackpad",
                    isSelected = activeScreen == ActiveScreen.TRACKPAD,
                    onClick = { activeScreen = ActiveScreen.TRACKPAD }
                )

                // Stream Toggle Button
                Button(
                    onClick = {
                        isStreamEnabled = !isStreamEnabled
                        if (isStreamEnabled) activeScreen = ActiveScreen.TRACKPAD
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStreamEnabled) Color(0xFF00C853) else Color(0xFF2C2C2C)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isStreamEnabled) "📺 Stream: ON" else "📺 Stream",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                // Open Tabs Button
                FloatingDockButton(
                    label = "📑 Tabs",
                    isSelected = activeScreen == ActiveScreen.TABS,
                    onClick = { activeScreen = ActiveScreen.TABS }
                )

                // Apps Button
                FloatingDockButton(
                    label = "🚀 Apps",
                    isSelected = activeScreen == ActiveScreen.APPS,
                    onClick = { activeScreen = ActiveScreen.APPS }
                )

                // Keyboard Toggle Button
                Button(
                    onClick = { showKeyboardBar = !showKeyboardBar },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showKeyboardBar) Color(0xFF2962FF) else Color(0xFF2C2C2C)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("⌨️ Type", fontSize = 11.sp, color = Color.White)
                }

                // Disconnect Button
                Button(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("🔌", fontSize = 11.sp, color = Color.White)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )
    }
}

@Composable
private fun FloatingDockButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF2962FF) else Color(0xFF262626)
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = Color.White
        )
    }
}
