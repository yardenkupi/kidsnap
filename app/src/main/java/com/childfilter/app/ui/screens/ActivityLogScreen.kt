package com.childfilter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.childfilter.app.data.AppPreferences
import com.childfilter.app.data.LogEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = AppPreferences.getInstance(context)
    val coroutineScope = rememberCoroutineScope()

    val log by prefs.getActivityLog().collectAsState(initial = emptyList())
    val children by prefs.getChildren().collectAsState(initial = emptyList())
    var showClearDialog by remember { mutableStateOf(false) }

    // Per-child tab state: 0 = "All", 1..N = per child
    var selectedChildTab by remember { mutableStateOf(0) }

    val childTabNames by remember(children) {
        derivedStateOf {
            listOf("All") + children.map { it.name }
        }
    }

    val filteredLog by remember {
        derivedStateOf {
            if (selectedChildTab == 0) {
                log
            } else {
                val targetChild = children.getOrNull(selectedChildTab - 1)?.name
                if (targetChild != null) {
                    log.filter { it.childName == targetChild }
                } else {
                    log
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear activity log?") },
            text = { Text("All log entries will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { prefs.clearActivityLog() }
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Activity Log", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear log",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Per-child tabs
            if (children.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedChildTab.coerceIn(0, childTabNames.lastIndex),
                    edgePadding = 12.dp
                ) {
                    childTabNames.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedChildTab == index,
                            onClick = { selectedChildTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }

            if (filteredLog.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "\uD83D\uDCCB",
                            fontSize = 48.sp
                        )
                        Text(
                            text = if (selectedChildTab == 0) "No activity yet"
                            else "No activity for this child",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            text = "Events will appear here once the watcher starts processing photos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLog) { entry ->
                        LogEntryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    val (icon, textColor, cardColor) = when (entry.type) {
        "match" -> Triple(
            "\u2705",  // check circle green
            Color(0xFF2E7D32),
            Color(0xFFE8F5E9)
        )
        "error" -> Triple(
            "\u26A0\uFE0F",  // warning red
            Color(0xFFC62828),
            Color(0xFFFFEBEE)
        )
        "info" -> Triple(
            "\u2139\uFE0F",  // info gray
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
        else -> Triple( // "scan"
            "\uD83D\uDD0D",  // search blue
            Color(0xFF1565C0),
            Color(0xFFE3F2FD)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 20.sp,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontWeight = if (entry.type == "match" || entry.type == "error") FontWeight.SemiBold else FontWeight.Normal
                )
                if (entry.childName != null) {
                    Text(
                        text = entry.childName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                Text(
                    text = relativeTime(entry.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private fun relativeTime(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val diffSeconds = diffMs / 1000
    val diffMinutes = diffSeconds / 60
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffSeconds < 60 -> "Just now"
        diffMinutes < 60 -> "${diffMinutes} min ago"
        diffHours < 24 -> "${diffHours} hour${if (diffHours != 1L) "s" else ""} ago"
        diffDays == 1L -> "Yesterday"
        else -> "${diffDays} days ago"
    }
}
