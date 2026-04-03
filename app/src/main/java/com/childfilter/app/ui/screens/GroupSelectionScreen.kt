package com.childfilter.app.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
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
import com.childfilter.app.service.GroupScanAccessibilityService
import com.childfilter.app.service.NotificationWatcherService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSelectionScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = AppPreferences.getInstance(context)
    val coroutineScope = rememberCoroutineScope()

    val knownGroups by prefs.getKnownGroups().collectAsState(initial = emptySet())
    val selectedGroups by prefs.getSelectedGroups().collectAsState(initial = emptySet())
    val isScanning by NotificationWatcherService.isScanning.collectAsState()
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

    // On Android < 12, check if our accessibility service is enabled.
    // (On Android 12+, getNotificationChannels() covers muted groups — no a11y needed.)
    val needsA11yService = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val a11yEnabled = remember(Unit) {
        if (!needsA11yService) true  // not needed on API 31+
        else {
            val am = context.getSystemService(AccessibilityManager::class.java)
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
                ?.any { it.id.contains(context.packageName) } == true
        }
    }
    val a11yServiceRunning by GroupScanAccessibilityService.isRunning.collectAsState()

    // Auto-scan as soon as the screen opens (or when access is granted)
    LaunchedEffect(hasNotificationAccess) {
        if (hasNotificationAccess) {
            NotificationWatcherService.triggerFullScan()
        }
    }

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
                    if (hasNotificationAccess) {
                        IconButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    NotificationWatcherService.triggerFullScan()
                                } else if (whatsappInstalled) {
                                    // Older Android: open WhatsApp so accessibility service scans
                                    val intent = context.packageManager
                                        .getLaunchIntentForPackage("com.whatsapp")
                                    if (intent != null) context.startActivity(intent)
                                }
                            },
                            enabled = !isScanning
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Rescan groups",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
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
                            text = if (hasNotificationAccess)
                                "Step 1 \u2713 \u2014 Notification access granted"
                            else
                                "Step 1 \u2014 Grant notification access",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!hasNotificationAccess) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Required so the app can read your WhatsApp group names automatically.",
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

            // Step 1b (Android < 12 only): Accessibility service for muted groups
            if (needsA11yService && hasNotificationAccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (a11yEnabled || a11yServiceRunning)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (a11yEnabled || a11yServiceRunning) Icons.Rounded.CheckCircle
                                else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = if (a11yEnabled || a11yServiceRunning)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (a11yEnabled || a11yServiceRunning)
                                    "Step 1b \u2713 \u2014 Group scanner active"
                                else
                                    "Step 1b (optional) \u2014 Enable group scanner",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (a11yEnabled || a11yServiceRunning)
                                "Reads WhatsApp\u2019s conversation list as you browse \u2014 imports groups even if notifications are completely off."
                            else
                                "Your device is Android ${Build.VERSION.RELEASE}. Enable this so groups with notifications turned off are also detected. Only group names are read \u2014 no messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!a11yEnabled && !a11yServiceRunning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable in Accessibility Settings")
                            }
                        }
                    }
                }
            }

            // Step 2: Auto-scan status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        !hasNotificationAccess -> MaterialTheme.colorScheme.surfaceVariant
                        knownGroups.isNotEmpty() -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                if (knownGroups.isNotEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = if (knownGroups.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isScanning -> "Scanning your WhatsApp groups\u2026"
                                knownGroups.isNotEmpty() -> "Step 2 \u2713 \u2014 ${knownGroups.size} group(s) found"
                                hasNotificationAccess -> "Step 2 \u2014 No groups found yet"
                                else -> "Step 2 \u2014 Waiting for notification access"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (hasNotificationAccess) {
                        val scanNote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            "All your WhatsApp groups are imported automatically from your notification history."
                        else
                            "Groups are detected from your WhatsApp notifications. If any are missing, open WhatsApp and browse those groups."
                        Text(
                            text = scanNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Fallback: open WhatsApp button for older devices or when list is empty
                        if (!isScanning && (knownGroups.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S)) {
                            if (whatsappInstalled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        val intent = context.packageManager
                                            .getLaunchIntentForPackage("com.whatsapp")
                                        if (intent != null) context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (knownGroups.isEmpty()) "Open WhatsApp to load groups"
                                        else "Open WhatsApp (add more groups)"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Step 3: Group list with checkboxes
            val groupList = knownGroups.toList().sorted()

            if (groupList.isNotEmpty()) {
                Text(
                    "Step 3 \u2014 Select which groups to monitor:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
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
            } else if (hasNotificationAccess && !isScanning) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            "No groups found.\n\nMake sure you have joined WhatsApp groups\nand that WhatsApp has sent at least\none notification on this device."
                        else
                            "No groups detected yet.\n\nOpen WhatsApp above and browse\nthe groups you want to monitor.",
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
