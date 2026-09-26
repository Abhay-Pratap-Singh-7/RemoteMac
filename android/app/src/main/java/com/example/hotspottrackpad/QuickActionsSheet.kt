package com.example.hotspottrackpad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class QuickAction(
    val actionKey: String,
    val icon: String,
    val label: String
)

@Composable
fun QuickActionsSheet(
    client: TrackpadClient,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = listOf(
        QuickAction("WAKE", "☀️", "Wake Display"),
        QuickAction("SLEEP", "🌙", "Sleep Display"),
        QuickAction("MISSION_CONTROL", "🎛️", "Mission Ctrl"),
        QuickAction("DESKTOP", "🖥️", "Desktop"),
        QuickAction("SPOTLIGHT", "🔍", "Spotlight"),
        QuickAction("APP_SWITCHER", "⇥", "App Switcher"),
        QuickAction("CLOSE_WINDOW", "❌", "Close ⌘W"),
        QuickAction("FULLSCREEN", "⛶", "Fullscreen"),
        QuickAction("COPY", "📋", "Copy ⌘C"),
        QuickAction("PASTE", "📥", "Paste ⌘V"),
        QuickAction("UNDO", "↩️", "Undo ⌘Z"),
        QuickAction("VOL_UP", "🔊", "Vol +"),
        QuickAction("VOL_DOWN", "🔉", "Vol -"),
        QuickAction("MUTE", "🔇", "Mute"),
        QuickAction("PLAY_PAUSE", "⏯️", "Play/Pause"),
        QuickAction("QUIT_APP", "🛑", "Quit ⌘Q")
    )

    Surface(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        color = Color(0xF5181818),
        tonalElevation = 12.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "⚡ Mac Controller",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("✕", fontSize = 14.sp, color = Color(0xFFAAAAAA))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(actions, key = { it.actionKey }) { action ->
                    ActionGridCard(
                        action = action,
                        onClick = {
                            if (action.actionKey == "SLEEP") {
                                client.sleepMac()
                            } else if (action.actionKey == "WAKE") {
                                client.wakeMac()
                            } else {
                                client.sendAction(action.actionKey)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionGridCard(
    action: QuickAction,
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
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = action.icon, fontSize = 16.sp)
            Text(
                text = action.label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
