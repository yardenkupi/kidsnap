package com.childfilter.app.service

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import com.childfilter.app.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationWatcherService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "NotificationWatcher"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

        // Exposed so GroupSelectionScreen can observe scanning state
        private val _isScanning = MutableStateFlow(false)
        val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

        @Volatile
        private var instance: NotificationWatcherService? = null

        /** Trigger a full group scan from the UI. No-op if listener not connected. */
        fun triggerFullScan() {
            instance?.performFullScan()
        }

        /** True when the notification listener is active and connected. */
        fun isConnected(): Boolean = instance != null
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        // Auto-scan immediately so the group list populates as soon as access is granted
        performFullScan()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        _isScanning.value = false
    }

    override fun onDestroy() {
        instance = null
        _isScanning.value = false
        scope.cancel()
        super.onDestroy()
    }

    // ── Full scan ────────────────────────────────────────────────────────────

    private fun performFullScan() {
        val prefs = AppPreferences.getInstance(applicationContext)
        scope.launch {
            _isScanning.value = true
            try {
                // Android 12+: read every WhatsApp notification channel directly.
                // Covers ALL groups regardless of notification state (active, silenced,
                // or fully disabled). Group channels have IDs ending with "@g.us".
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    scanWhatsAppChannels(prefs)
                }
                // Android < 12: group discovery is handled by GroupScanAccessibilityService.
                // Here we only seed lastActiveGroup from currently visible notifications.
                seedLastActiveGroupFromVisible(prefs)
            } finally {
                _isScanning.value = false
            }
        }
    }

    /** Updates lastActiveGroup from whichever WhatsApp notification is currently on-screen. */
    private suspend fun seedLastActiveGroupFromVisible(prefs: AppPreferences) {
        try {
            val active = getActiveNotifications() ?: return
            for (sbn in active) processNotification(sbn, prefs)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading active notifications", e)
        }
    }

    /**
     * Android 12+ only.
     *
     * WhatsApp notification channel IDs mirror the WhatsApp JID format:
     *   - Groups:       "<timestamp>-<timestamp>@g.us"
     *   - Individuals:  "<phone>@s.whatsapp.net" or "<phone>@c.us"
     *   - System:       fixed IDs like "messages_v2", "calls_v2", etc.
     *
     * Filtering by "@g.us" suffix gives us group channels only.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun scanWhatsAppChannels(prefs: AppPreferences) {
        val userHandle = android.os.Process.myUserHandle()
        val packages = listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)

        for (pkg in packages) {
            try {
                val channels = getNotificationChannels(pkg, userHandle) ?: continue
                var groupsFound = 0
                for (channel in channels) {
                    val name = channel.name?.toString()?.trim() ?: continue
                    if (name.isBlank()) continue
                    if (channel.id.endsWith("@g.us")) {
                        prefs.addKnownGroup(name)
                        groupsFound++
                    }
                }
                Log.d(TAG, "Channel scan ($pkg): $groupsFound group(s) in ${channels.size} channels")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to scan channels for $pkg", e)
            }
        }
    }

    // ── Passive notification listener ────────────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val prefs = AppPreferences.getInstance(applicationContext)
        scope.launch { processNotification(sbn, prefs) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* No-op */ }

    private suspend fun processNotification(sbn: StatusBarNotification, prefs: AppPreferences) {
        val pkg = sbn.packageName
        if (pkg != WHATSAPP_PACKAGE && pkg != WHATSAPP_BUSINESS_PACKAGE) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // Group discovery is handled entirely by:
        //   • Android 12+  → getNotificationChannels() in scanWhatsAppChannels()
        //   • Android < 12 → GroupScanAccessibilityService reads the conversation list
        // Both cover active, silenced, and fully-disabled-notification groups alike.
        //
        // The only job here is tracking WHICH group was last active and WHEN, so the
        // 5-minute window filter in FolderWatcherService knows whether a new WhatsApp
        // photo should be processed.
        val isGroupConversation = extras.getBoolean("android.isGroupConversation", false)
        val hasSenderColonFormat = text.contains(": ")
        val hasSubText = extras.getCharSequence("android.subText") != null
        val hasSilentSummaryFormat = text.matches(Regex("""\d+\s+(new\s+)?messages?""", RegexOption.IGNORE_CASE))

        val isGroup = isGroupConversation || hasSenderColonFormat || hasSubText || hasSilentSummaryFormat

        if (isGroup) {
            Log.d(TAG, "Active group: '$title'")
            prefs.setLastActiveGroup(title, System.currentTimeMillis())
        }
    }
}
