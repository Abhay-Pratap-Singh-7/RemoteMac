package com.example.hotspottrackpad

import android.app.Activity
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun AiAssistantSheet(
    client: TrackpadClient,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }
    var showKeyEditor by remember { mutableStateOf(apiKey.isEmpty()) }
    var userPrompt by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var lastExecution by remember { mutableStateOf<Pair<String, String>?>(null) }
    var statusError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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

                Row {
                    IconButton(
                        onClick = { showKeyEditor = !showKeyEditor },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Text(if (apiKey.isEmpty()) "🔑" else "⚙️", fontSize = 13.sp)
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Text("✕", fontSize = 14.sp, color = Color(0xFFAAAAAA))
                    }
                }
            }

            // API Key Input Card
            if (showKeyEditor) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Gemini API Key (Fastest: gemini-2.5-flash)", color = Color(0xFF90CAF9), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                prefs.edit().putString("gemini_key", it.trim()).apply()
                            },
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
                                if (apiKey.isNotBlank()) showKeyEditor = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2962FF)),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text("Save Key", fontSize = 11.sp, color = Color.White)
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
                            statusError = "Voice speech recognition not available"
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
                        if (prompt.isNotEmpty() && apiKey.isNotBlank()) {
                            isLoading = true
                            statusError = null
                            scope.launch {
                                executeGeminiTask(prompt, apiKey, client) { success, summary, cmd, err ->
                                    isLoading = false
                                    if (success) {
                                        lastExecution = Pair(summary, cmd)
                                        userPrompt = ""
                                    } else {
                                        statusError = err
                                    }
                                }
                            }
                        } else if (apiKey.isBlank()) {
                            showKeyEditor = true
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text("Run", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Loading Indicator
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF64B5F6), strokeWidth = 2.dp)
                    Text("Gemini is generating Mac command...", color = Color(0xFF64B5F6), fontSize = 11.sp)
                }
            }

            // Error Display
            if (statusError != null) {
                Text(
                    text = "⚠️ $statusError",
                    color = Color(0xFFEF5350),
                    fontSize = 11.sp
                )
            }

            // Last Execution Result Card
            lastExecution?.let { (summary, cmd) ->
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
                            Text(summary, color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = cmd,
                            color = Color(0xFFE0E0E0),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2
                        )
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

private suspend fun executeGeminiTask(
    prompt: String,
    apiKey: String,
    client: TrackpadClient,
    callback: (Boolean, String, String, String?) -> Unit
) {
    withContext(Dispatchers.IO) {
        val models = listOf("gemini-2.5-flash", "gemini-1.5-flash")
        var lastErr = ""

        for (model in models) {
            try {
                val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${apiKey.trim()}"
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 6000
                    readTimeout = 8000
                }

                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "You are a macOS automation agent. Convert this user task into an executable macOS command: \"$prompt\". Output ONLY JSON: {\"type\": \"shell\"|\"applescript\"|\"open_url\"|\"launch_app\", \"command\": \"...\", \"summary\": \"...\"}")
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("response_mime_type", "application/json")
                        put("temperature", 0.1)
                    })
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(payload.toString())
                writer.flush()
                writer.close()

                val code = conn.responseCode
                if (code in 200..299) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val responseStr = reader.readText()
                    reader.close()

                    val json = JSONObject(responseStr)
                    val candidates = json.getJSONArray("candidates")
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val text = parts.getJSONObject(0).getString("text")

                    val resultJson = JSONObject(text.trim())
                    val type = resultJson.getString("type")
                    val command = resultJson.getString("command")
                    val summary = resultJson.optString("summary", "Executed task")

                    // Run on Mac!
                    client.executeAiCommand(type, command)

                    withContext(Dispatchers.Main) {
                        callback(true, summary, command, null)
                    }
                    return@withContext
                } else {
                    val errStream = conn.errorStream ?: conn.inputStream
                    val reader = BufferedReader(InputStreamReader(errStream))
                    lastErr = "HTTP $code: ${reader.readText().take(120)}"
                    reader.close()
                }
            } catch (e: Exception) {
                lastErr = e.localizedMessage ?: "Network error"
            }
        }

        withContext(Dispatchers.Main) {
            callback(false, "", "", lastErr)
        }
    }
}
