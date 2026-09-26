package com.example.hotspottrackpad

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AiAssistantSheet(
    client: TrackpadClient,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAiKeyConfigured by client.isAiKeyConfigured.collectAsState()
    val isAiRunning by client.isAiRunning.collectAsState()
    val aiResult by client.aiResult.collectAsState()

    var showKeyEditor by remember { mutableStateOf(!isAiKeyConfigured) }
    var keyInput by remember { mutableStateOf("") }
    var userPrompt by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        client.checkAiKey()
    }

    LaunchedEffect(isAiKeyConfigured) {
        if (isAiKeyConfigured) {
            showKeyEditor = false
        }
    }

    // Voice dictation launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                userPrompt = spoken
            }
        }
    }

    Surface(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        color = Color(0xF5161616),
        tonalElevation = 14.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("✨", fontSize = 16.sp)
                    Text("Gemini Mac Assistant", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showKeyEditor = !showKeyEditor },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Text(if (!isAiKeyConfigured) "🔑" else "⚙️", fontSize = 13.sp)
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Text("✕", fontSize = 14.sp, color = Color(0xFFAAAAAA))
                    }
                }
            }

            // Key status badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isAiKeyConfigured) Color(0xFF1B2E1D) else Color(0xFF332211), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(if (isAiKeyConfigured) "🟢" else "🟠", fontSize = 10.sp)
                Text(
                    text = if (isAiKeyConfigured) "Server Key: Active on Mac" else "Server Key: Missing (gemini_key.txt)",
                    color = if (isAiKeyConfigured) Color(0xFF81C784) else Color(0xFFFFB74D),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // API Key Input Card (Server-side stored)
            if (showKeyEditor || !isAiKeyConfigured) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Store Gemini API Key on Mac Server",
                            color = Color(0xFF90CAF9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "The key will be saved to macOS/gemini_key.txt on your Mac and invoked directly by the server.",
                            color = Color(0xFF888888),
                            fontSize = 10.sp
                        )
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            placeholder = { Text("Paste AI Studio API Key", color = Color(0xFF666666), fontSize = 11.sp) },
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
                            onClick = {
                                if (keyInput.isNotBlank()) {
                                    client.setServerAiKey(keyInput.trim())
                                    keyInput = ""
                                    showKeyEditor = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2962FF)),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text("Save Key to Mac Server", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }

            // Prompt Input & Voice Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    placeholder = { Text("Type or dictate task...", color = Color(0xFF666666), fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color(0xFF333333)
                    )
                )

                // Voice Dictation Button
                IconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a Mac command...")
                        }
                        try {
                            voiceLauncher.launch(intent)
                        } catch (e: Exception) {
                            voiceError = "Voice speech recognition not available"
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF2A2A2A), shape = CircleShape)
                ) {
                    Text("🎙️", fontSize = 15.sp)
                }

                // Send Button
                Button(
                    onClick = {
                        val prompt = userPrompt.trim()
                        if (prompt.isNotEmpty()) {
                            if (!isAiKeyConfigured) {
                                showKeyEditor = true
                            } else {
                                client.sendAiTask(prompt)
                                userPrompt = ""
                            }
                        }
                    },
                    enabled = !isAiRunning,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text("Run", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Loading Indicator
            if (isAiRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF64B5F6), strokeWidth = 2.dp)
                    Text("Mac server is executing AI task with Gemini...", color = Color(0xFF64B5F6), fontSize = 11.sp)
                }
            }

            // Voice Error Display
            if (voiceError != null) {
                Text(
                    text = "⚠️ $voiceError",
                    color = Color(0xFFEF5350),
                    fontSize = 11.sp
                )
            }

            // Execution Result / Error Card
            aiResult?.let { result ->
                if (result.success) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E1D)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("✅", fontSize = 12.sp)
                                Text(result.summary, color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (result.command.isNotBlank()) {
                                Text(
                                    text = result.command,
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                } else if (!result.error.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF331515)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("⚠️", fontSize = 12.sp)
                                Text("AI Error", color = Color(0xFFEF5350), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = result.error,
                                color = Color(0xFFFFCDD2),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF262626))

            // Quick Preset Prompts
            Text("⚡ Quick Prompts", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

            val presets = listOf(
                "🔊 Set volume to 50%",
                "📸 Take a screenshot",
                "🖥️ Open Downloads folder",
                "🌐 Open YouTube in browser",
                "🔒 Lock my Mac screen",
                "🗑️ Empty the Trash"
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                userPrompt = preset.substring(preset.indexOf(" ") + 1)
                            },
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF202020))
                    ) {
                        Text(
                            text = preset,
                            color = Color(0xFFCCCCCC),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
