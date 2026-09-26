package com.example.hotspottrackpad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TabsOverlaySheet(
    client: TrackpadClient,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs by client.openTabs.collectAsState()

    LaunchedEffect(Unit) {
        client.fetchOpenTabs()
    }

    Surface(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        color = Color(0xF5181818),
        tonalElevation = 12.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Header: Title, Refresh, Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📑 Open Tabs",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { client.fetchOpenTabs() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("🔄", fontSize = 14.sp)
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("✕", fontSize = 14.sp, color = Color(0xFFAAAAAA))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (tabs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFF2962FF),
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Fetching open tabs...", color = Color(0xFF888888), fontSize = 11.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        MinimalTabCard(
                            tab = tab,
                            onClick = {
                                client.switchTab(tab)
                                onDismiss() // Switch immediately back to trackpad!
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalTabCard(
    tab: MacTab,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF242424))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // App Letter Badge
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0xFF333333), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.appName.take(1).uppercase(),
                    color = Color(0xFF64B5F6),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            // Minimal Title and App Name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tab.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tab.appName,
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
