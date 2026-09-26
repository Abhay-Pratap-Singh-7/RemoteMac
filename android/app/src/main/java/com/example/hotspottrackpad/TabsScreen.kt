package com.example.hotspottrackpad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun TabsScreen(
    client: TrackpadClient,
    snackbarHostState: SnackbarHostState
) {
    val tabs by client.openTabs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val filteredTabs = remember(tabs, searchQuery) {
        if (searchQuery.isBlank()) tabs
        else tabs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.appName.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(Unit) {
        client.fetchOpenTabs()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Search and Refresh Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter open windows & tabs...", color = Color(0xFF777777)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF2962FF),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E)
                )
            )

            Button(
                onClick = { client.fetchOpenTabs() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text("🔄", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (tabs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF2962FF))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Fetching open windows & tabs...", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                }
            }
        } else if (filteredTabs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No open tabs/windows matching '$searchQuery'", color = Color(0xFF777777))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 68.dp) // space for floating dock
            ) {
                items(filteredTabs, key = { it.id }) { tab ->
                    TabCardGridItem(
                        tab = tab,
                        onClick = {
                            client.switchTab(tab)
                            scope.launch {
                                snackbarHostState.showSnackbar("Switched to: ${tab.title.take(30)}...")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabCardGridItem(
    tab: MacTab,
    onClick: () -> Unit
) {
    val isBrowserTab = tab.type == "chrome" || tab.type == "brave" || tab.type == "safari"
    val badgeColor = if (isBrowserTab) Color(0xFF2962FF) else Color(0xFFAB47BC)
    val badgeText = if (isBrowserTab) "Tab" else "Window"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFF2D2D2D), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.appName.take(1).uppercase(),
                            color = Color(0xFF64B5F6),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = tab.appName,
                        color = Color(0xFF90CAF9),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = tab.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            Text(
                text = "Tap to switch ➜",
                color = Color(0xFF00E676),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
