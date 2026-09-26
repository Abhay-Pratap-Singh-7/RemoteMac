package com.example.hotspottrackpad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Fetching open windows & tabs...", color = Color(0xFFAAAAAA), fontSize = 14.sp)
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(filteredTabs, key = { it.id }) { tab ->
                    TabItemCard(
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
private fun TabItemCard(
    tab: MacTab,
    onClick: () -> Unit
) {
    val isBrowserTab = tab.type == "chrome" || tab.type == "brave" || tab.type == "safari"
    val badgeColor = if (isBrowserTab) Color(0xFF2962FF) else Color(0xFF7B1FA2)
    val badgeText = if (isBrowserTab) "Tab" else "Window"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = tab.appName,
                        color = Color(0xFF90CAF9),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        color = badgeColor.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = tab.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Switch ➜",
                color = Color(0xFF00E676),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
