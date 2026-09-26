package com.example.hotspottrackpad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
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

enum class AppTab {
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
    var currentTab by remember { mutableStateOf(AppTab.TRACKPAD) }
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    var showKeyboardBar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        text = connectedIp,
                        color = Color(0xFF00E676),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showKeyboardBar = !showKeyboardBar },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showKeyboardBar) Color(0xFF2962FF) else Color(0xFF2C2C2C)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(if (showKeyboardBar) "⌨️ Hide" else "⌨️ Type", fontSize = 12.sp, color = Color.White)
                    }

                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Disconnect", fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            // Tab Navigation (Trackpad vs Open Tabs vs Launch Apps)
            TabRow(
                selectedTabIndex = currentTab.ordinal,
                containerColor = Color(0xFF181818),
                contentColor = Color(0xFF2962FF)
            ) {
                Tab(
                    selected = currentTab == AppTab.TRACKPAD,
                    onClick = { currentTab = AppTab.TRACKPAD },
                    text = {
                        Text(
                            "🖱️ Trackpad",
                            color = if (currentTab == AppTab.TRACKPAD) Color(0xFF64B5F6) else Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }
                )
                Tab(
                    selected = currentTab == AppTab.TABS,
                    onClick = { currentTab = AppTab.TABS },
                    text = {
                        Text(
                            "📑 Open Tabs",
                            color = if (currentTab == AppTab.TABS) Color(0xFF64B5F6) else Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }
                )
                Tab(
                    selected = currentTab == AppTab.APPS,
                    onClick = { currentTab = AppTab.APPS },
                    text = {
                        Text(
                            "🚀 Apps",
                            color = if (currentTab == AppTab.APPS) Color(0xFF64B5F6) else Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }
                )
            }

            // Real-time Automatic Keyboard Typing Section (when enabled)
            if (showKeyboardBar) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Real-time input field: sends every character or backspace immediately!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = textValue,
                                onValueChange = { newVal ->
                                    val oldText = textValue.text
                                    val newText = newVal.text

                                    if (newText.length > oldText.length) {
                                        // User typed new characters: send them instantly to Mac
                                        val added = newText.substring(oldText.length)
                                        client.typeText(added)
                                    } else if (newText.length < oldText.length) {
                                        // User pressed backspace: send backspace instantly to Mac
                                        val count = oldText.length - newText.length
                                        repeat(count) {
                                            client.sendKey("BACKSPACE")
                                        }
                                    }
                                    textValue = newVal
                                },
                                placeholder = { Text("Type here (streams live to Mac)...", color = Color(0xFF777777)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && textValue.text.isEmpty()) {
                                            client.sendKey("BACKSPACE")
                                            true
                                        } else false
                                    },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    client.sendKey("ENTER")
                                }),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF00E676),
                                    unfocusedBorderColor = Color(0xFF444444)
                                )
                            )

                            Button(
                                onClick = { textValue = TextFieldValue("") },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Text("Clear", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                            }
                        }

                        // Quick action keys: instantly send Enter, Del, Space, Tab, Esc
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { client.sendKey("ENTER") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("↵ Enter", fontSize = 11.sp, color = Color.White)
                            }

                            Button(
                                onClick = { client.sendKey("BACKSPACE") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("⌫ Del", fontSize = 11.sp, color = Color.White)
                            }

                            Button(
                                onClick = { client.sendKey("SPACE") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("␣ Space", fontSize = 11.sp, color = Color.White)
                            }

                            Button(
                                onClick = { client.sendKey("TAB") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("⇥ Tab", fontSize = 11.sp, color = Color.White)
                            }

                            Button(
                                onClick = { client.sendKey("ESCAPE") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("⎋ Esc", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Tab Content
            when (currentTab) {
                AppTab.TRACKPAD -> {
                    // Trackpad Surface
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

                    // Bottom Physical Buttons
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

                AppTab.TABS -> {
                    TabsScreen(
                        client = client,
                        snackbarHostState = snackbarHostState
                    )
                }

                AppTab.APPS -> {
                    AppsScreen(
                        client = client,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }
}
