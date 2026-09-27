package com.example.hotspottrackpad

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun MjpegStreamView(
    client: TrackpadClient,
    serverIp: String,
    port: Int = 8081,
    modifier: Modifier = Modifier
) {
    val isRelay by client.isRelayMode.collectAsState()
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (isRelay) {
        LaunchedEffect(Unit) {
            client.send("START_STREAM")
            withContext(Dispatchers.Default) {
                var reusableBitmap: Bitmap? = null
                val decodeOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inMutable = true
                }

                for (bytes in client.streamFrameChannel) {
                    if (!isActive) break
                    if (bytes.isEmpty()) continue
                    try {
                        val currentTarget = reusableBitmap
                        if (currentTarget != null && !currentTarget.isRecycled) {
                            decodeOptions.inBitmap = currentTarget
                        }
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                        if (bmp != null) {
                            reusableBitmap = bmp
                            currentBitmap = bmp
                        }
                    } catch (e: IllegalArgumentException) {
                        decodeOptions.inBitmap = null
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                        if (bmp != null) {
                            reusableBitmap = bmp
                            currentBitmap = bmp
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    } else {
        val streamUrl = remember(serverIp, port) { "http://$serverIp:$port/stream" }

        LaunchedEffect(streamUrl) {
            currentBitmap = null
            errorMessage = null

            withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                var inputStream: BufferedInputStream? = null
                var reusableBitmap: Bitmap? = null
                val decodeOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inMutable = true
                }
                try {
                    val url = URL(streamUrl)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000
                        readTimeout = 6000
                        doInput = true
                        useCaches = false
                    }
                    inputStream = BufferedInputStream(connection.inputStream)
                    val reader = JpegFrameReader(inputStream)

                    while (isActive) {
                        val frameBytes = reader.readNextFrame() ?: break
                        try {
                            val currentTarget = reusableBitmap
                            if (currentTarget != null && !currentTarget.isRecycled) {
                                decodeOptions.inBitmap = currentTarget
                            }
                            val bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size, decodeOptions)
                            if (bitmap != null) {
                                reusableBitmap = bitmap
                                currentBitmap = bitmap
                            }
                        } catch (e: IllegalArgumentException) {
                            decodeOptions.inBitmap = null
                            val bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size, decodeOptions)
                            if (bitmap != null) {
                                reusableBitmap = bitmap
                                currentBitmap = bitmap
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        errorMessage = e.localizedMessage ?: "Failed to connect to stream"
                    }
                } finally {
                    try { inputStream?.close() } catch (_: Exception) {}
                    try { connection?.disconnect() } catch (_: Exception) {}
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val bmp = currentBitmap
        if (bmp != null) {
            val aspectRatio = remember(bmp) {
                if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 16f / 9f
            }
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Mac Live Screen",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
            )
        } else if (errorMessage != null) {
            Text(
                text = "Screen Stream: $errorMessage\nEnsure Screen Recording is permitted in Mac System Settings",
                color = Color(0xFFEF5350),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(color = Color(0xFF64B5F6), strokeWidth = 2.dp)
                Text(
                    text = if (isRelay) "Receiving Mac Screen via Cloud Relay..." else "Connecting to Screen Stream...",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp
                )
            }
        }
    }
}

class JpegFrameReader(private val inputStream: InputStream) {
    private val buffer = ByteArray(65536)

    fun readNextFrame(): ByteArray? {
        val output = ByteArrayOutputStream()
        var foundStart = false
        var prevByte = -1

        while (true) {
            val b = inputStream.read()
            if (b == -1) return null

            if (!foundStart) {
                if (prevByte == 0xFF && b == 0xD8) {
                    foundStart = true
                    output.write(0xFF)
                    output.write(0xD8)
                }
                prevByte = b
            } else {
                output.write(b)
                if (prevByte == 0xFF && b == 0xD9) {
                    return output.toByteArray()
                }
                prevByte = b
            }
        }
    }
}
