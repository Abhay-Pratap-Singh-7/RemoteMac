package com.example.hotspottrackpad

import androidx.compose.animation.*
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

@Composable
fun TrackpadScreen(
    client: TrackpadClient,
    connectedIp: String,
    onDisconnect: () -> Unit
) {
    var isStreamEnabled by remember { mutableStateOf(false) }
    var showTabsOverlay by remember { mutableStateOf(false) }
    var showActionsOverlay by remember { mutableStateOf(false) }
    var showAppsOverlay by remember { mutableStateOf(false) }
    var showAiOverlay by remember { mutableStateOf(false) }
    var showKeyboardBar by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    val snackbarHostState = remember { SnackbarHostState() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // LEFT VERTICAL DOCK: Trackpad, Stream, Fast Controller Actions
        Column(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight()
                .background(Color(0xFF141414))
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Trackpad reset button
            SideDockButton(
                icon = "🖱️",
                label = "Pad",
                isSelected = !showTabsOverlay && !showAppsOverlay && !showActionsOverlay && !showAiOverlay,
                onClick = {
                    showTabsOverlay = false
                    showAppsOverlay = false
                    showActionsOverlay = false
                    showAiOverlay = false
                }
            )

            // Stream Toggle
            SideDockButton(
                icon = "📺",
                label = if (isStreamEnabled) "Live" else "Stream",
                isSelected = isStreamEnabled,
                activeColor = Color(0xFF00C853),
                onClick = { isStreamEnabled = !isStreamEnabled }
            )

            // Mac Controller Actions
            SideDockButton(
                icon = "⚡",
                label = "Actions",
                isSelected = showActionsOverlay,
                activeColor = Color(0xFFFFA000),
                onClick = {
                    showActionsOverlay = !showActionsOverlay
                    if (showActionsOverlay) {
                        showTabsOverlay = false
                        showAppsOverlay = false
                        showAiOverlay = false
                    }
                }
            )
        }

        // CENTER MAIN CONTENT (Trackpad & Stream, with overlay slide-outs)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Live Stream (centered, aspect ratio preserved) or Trackpad Guide
            if (isStreamEnabled) {
                MjpegStreamView(
                    client = client,
                    serverIp = connectedIp,
                    port = 8081,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF101010)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🖱️ Trackpad Active\n1 Finger: Move Cursor & Tap to Click\n2 Fingers: Scroll & Tap to Right-Click\nTap 📺 to Toggle Mac Screen Stream",
                        color = Color(0xFF555555),
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Touch Gesture Surface (Overlays entire center area; NO L/R buttons)
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

            // Slide-out Left Overlay: Quick Mac Actions
            androidx.compose.animation.AnimatedVisibility(
                visible = showActionsOverlay,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                QuickActionsSheet(
                    client = client,
                    onDismiss = { showActionsOverlay = false }
                )
            }

            // Slide-out Right Overlay: AI Assistant Sheet
            androidx.compose.animation.AnimatedVisibility(
                visible = showAiOverlay,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                AiAssistantSheet(
                    client = client,
                    onDismiss = { showAiOverlay = false }
                )
            }

            // Slide-out Right Overlay: Minimal Open Tabs Sheet
            androidx.compose.animation.AnimatedVisibility(
                visible = showTabsOverlay,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                TabsOverlaySheet(
                    client = client,
                    onDismiss = {
                        // Immediately dismiss and switch to trackpad!
                        showTabsOverlay = false
                    }
                )
            }

            // Slide-out Right Overlay: Mac Apps Launcher
            androidx.compose.animation.AnimatedVisibility(
                visible = showAppsOverlay,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(320.dp)
                    .fillMaxHeight()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    color = Color(0xF5181818),
                    tonalElevation = 12.dp,
                    shadowElevation = 16.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🚀 Launch Apps", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(onClick = { showAppsOverlay = false }, modifier = Modifier.size(28.dp)) {
                                Text("✕", color = Color(0xFFAAAAAA))
                            }
                        }
                        AppsScreen(
                            client = client,
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }

            // Floating Real-Time Keyboard Input Bar (at top of center area)
            if (showKeyboardBar) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 16.dp, end = 16.dp)
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
                                    .height(44.dp)
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("Clear", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                            }

                            Button(
                                onClick = { showKeyboardBar = false },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("✕", fontSize = 11.sp, color = Color.White)
                            }
                        }

                        // Quick Keys Row
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

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }

        // RIGHT VERTICAL DOCK: AI Assistant, Tabs, Apps, Type, Disconnect
        Column(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight()
                .background(Color(0xFF141414))
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // AI Assistant Button
            SideDockButton(
                icon = "✨",
                label = "AI Task",
                isSelected = showAiOverlay,
                activeColor = Color(0xFF7C4DFF),
                onClick = {
                    showAiOverlay = !showAiOverlay
                    if (showAiOverlay) {
                        showTabsOverlay = false
                        showAppsOverlay = false
                        showActionsOverlay = false
                    }
                }
            )

            // Tabs Button
            SideDockButton(
                icon = "📑",
                label = "Tabs",
                isSelected = showTabsOverlay,
                activeColor = Color(0xFF2962FF),
                onClick = {
                    showTabsOverlay = !showTabsOverlay
                    if (showTabsOverlay) {
                        showAppsOverlay = false
                        showActionsOverlay = false
                        showAiOverlay = false
                    }
                }
            )

            // Apps Button
            SideDockButton(
                icon = "🚀",
                label = "Apps",
                isSelected = showAppsOverlay,
                activeColor = Color(0xFFAB47BC),
                onClick = {
                    showAppsOverlay = !showAppsOverlay
                    if (showAppsOverlay) {
                        showTabsOverlay = false
                        showActionsOverlay = false
                        showAiOverlay = false
                    }
                }
            )

            // Keyboard Button
            SideDockButton(
                icon = "⌨️",
                label = if (showKeyboardBar) "Hide" else "Type",
                isSelected = showKeyboardBar,
                activeColor = Color(0xFF00B0FF),
                onClick = { showKeyboardBar = !showKeyboardBar }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Disconnect Button
            Button(
                onClick = onDisconnect,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔌", fontSize = 14.sp)
                    Text("Exit", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SideDockButton(
    icon: String,
    label: String,
    isSelected: Boolean,
    activeColor: Color = Color(0xFF2962FF),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) activeColor else Color(0xFF222222)
        ),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}
