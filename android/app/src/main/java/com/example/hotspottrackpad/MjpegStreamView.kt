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
    serverIp: String,
    port: Int = 8081,
    modifier: Modifier = Modifier
) {
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val streamUrl = remember(serverIp, port) { "http://$serverIp:$port/stream" }

    LaunchedEffect(streamUrl) {
        currentBitmap = null
        errorMessage = null

        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            var inputStream: BufferedInputStream? = null
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
                    val bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
                    if (bitmap != null) {
                        currentBitmap = bitmap
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
            CircularProgressIndicator(
                color = Color(0xFF2962FF),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

private class JpegFrameReader(private val stream: InputStream) {
    fun readNextFrame(): ByteArray? {
        val out = ByteArrayOutputStream()
        var prev: Int
        var curr = -1
        var inFrame = false

        while (true) {
            prev = curr
            curr = stream.read()
            if (curr == -1) return null

            if (!inFrame) {
                if (prev == 0xFF && curr == 0xD8) {
                    inFrame = true
                    out.write(0xFF)
                    out.write(0xD8)
                }
            } else {
                out.write(curr)
                if (prev == 0xFF && curr == 0xD9) {
                    return out.toByteArray()
                }
            }
        }
    }
}
