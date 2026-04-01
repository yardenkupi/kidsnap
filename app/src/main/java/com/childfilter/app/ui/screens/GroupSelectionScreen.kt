package com.childfilter.app.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavController
import com.childfilter.app.data.AppPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSelectionScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = AppPreferences.getInstance(context)
    val coroutineScope = rememberCoroutineScope()

    val knownGroups by prefs.getKnownGroups().collectAsState(initial = emptySet())
    val selectedGroups by prefs.getSelectedGroups().collectAsState(initial = emptySet())
    var currentSelected by remember(selectedGroups) { mutableStateOf(selectedGroups) }
    var showPlayProtectHelp by remember { mutableStateOf(true) }
    var showAddManualDialog by remember { mutableStateOf(false) }
    var manualGroupName by remember { mutableStateOf("") }

    val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
    val hasNotificationAccess = enabledListeners.contains(context.packageName)

    val whatsappInstalled = try {
        context.packageManager.getPackageInfo("com.whatsapp", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Groups", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (knownGroups.isNotEmpty()) {
                        IconButton(onClick = {
                            // Reopen this screen to refresh
                        }) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Play Protect / sideload guidance
            if (!hasNotificationAccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google Play Protect warning?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showPlayProtectHelp = !showPlayProtectHelp }) {
                                Text(if (showPlayProtectHelp) "Hide" else "Show steps")
                            }
                        }
                        if (showPlayProtectHelp) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This app is installed directly (sideloaded), not from the Play Store. " +
                                    "Google Play Protect may block it or prevent notification access. " +
                                    "Follow these steps once:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "1. Open Play Store → tap your profile photo → Play Protect\n" +
                                    "2. Tap the gear icon (⚙) → turn off \"Scan apps with Play Protect\"\n" +
                                    "3. Or when prompted \"Unsafe app blocked\" → tap More details → Install anyway\n\n" +
                                    "Then grant notification access below (Step 1).\n\n" +
                                    "Samsung devices: Settings → Apps → Special app access → Notification access → KidSnap",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://support.google.com/googleplay/answer/2812853"))
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Learn more about Play Protect")
                            }
                        }
                    }
                }
            }

            // Step 1: Notification access
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasNotificationAccess)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hasNotificationAccess) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (hasNotificationAccess)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasNotificationAccess) "Step 1 ✓ — Notification access granted" else "Step 1 — Grant notification access",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!hasNotificationAccess) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Required so the app can detect your WhatsApp group names automatically.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Notification Access")
                        }
                    }
                }
            }

            // Step 2: Open WhatsApp to scan
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (knownGroups.isNotEmpty())
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (knownGroups.isNotEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (knownGroups.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (knownGroups.isNotEmpty())
                                "Step 2 ✓ — ${knownGroups.size} group(s) detected"
                            else
                                "Step 2 — Open WhatsApp to scan groups",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Open WhatsApp and tap on each group you want to monitor. Groups appear here automatically as you browse them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasNotificationAccess && whatsappInstalled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                val intent = context.packageManager
                                    .getLaunchIntentForPackage("com.whatsapp")
                                if (intent != null) context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open WhatsApp")
                        }
                    }
                }
            }

            // Step 3: Select groups
            val groupList = knownGroups.toList().sorted()

            if (groupList.isNotEmpty()) {
                Text(
                    "Step 3 — Select which groups to monitor:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(groupList) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = currentSelected.contains(group),
                                    onCheckedChange = { checked ->
                                        currentSelected = if (checked) {
                                            currentSelected + group
                                        } else {
                                            currentSelected - group
                                        }
                                        coroutineScope.launch {
                                            prefs.saveSelectedGroups(currentSelected)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = group,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            } else if (hasNotificationAccess) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No groups detected yet.\n\nOpen WhatsApp above and tap on\neach group you want to monitor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            TextButton(
                onClick = { manualGroupName = ""; showAddManualDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Add group name manually")
            }

            FilledTonalButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (currentSelected.isEmpty()) "Done" else "Done (${currentSelected.size} selected)")
            }
        }
    }

    if (showAddManualDialog) {
        AlertDialog(
            onDismissRequest = { showAddManualDialog = false },
            title = { Text("Add Group Manually") },
            text = {
                Column {
                    Text(
                        "Type the exact WhatsApp group name as it appears in the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualGroupName,
                        onValueChange = { manualGroupName = it },
                        label = { Text("Group name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = manualGroupName.trim()
                        if (name.isNotEmpty()) {
                            coroutineScope.launch {
                                prefs.addKnownGroup(name)
                                currentSelected = currentSelected + name
                                prefs.saveSelectedGroups(currentSelected)
                            }
                        }
                        showAddManualDialog = false
                    },
                    enabled = manualGroupName.isNotBlank()
                ) { Text("Add & Select") }
            },
            dismissButton = {
                TextButton(onClick = { showAddManualDialog = false }) { Text("Cancel") }
            }
        )
    }
}
