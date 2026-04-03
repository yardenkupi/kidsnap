package com.childfilter.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.childfilter.app.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationWatcherService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "NotificationWatcher"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Immediately scan any WhatsApp notifications already on-screen when
        // the user grants notification access — this populates the group list
        // without needing to wait for new messages.
        try {
            val active = getActiveNotifications() ?: return
            val prefs = AppPreferences.getInstance(applicationContext)
            scope.launch {
                for (sbn in active) {
                    processNotification(sbn, prefs)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning existing notifications", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val prefs = AppPreferences.getInstance(applicationContext)
        scope.launch { processNotification(sbn, prefs) }
    }

    private suspend fun processNotification(sbn: StatusBarNotification, prefs: AppPreferences) {
        val pkg = sbn.packageName
        if (pkg != WHATSAPP_PACKAGE && pkg != WHATSAPP_BUSINESS_PACKAGE) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // Group messages: text is "SenderName: message body"
        // Personal messages: text is just the message body (no ": " prefix)
        // Also accept notifications where the title looks like a group (contains spaces,
        // longer than a typical contact name pattern, or subtext is set for bundles)
        val isGroupMessage = text.contains(": ") ||
            extras.getCharSequence("android.subText") != null

        if (isGroupMessage) {
            Log.d(TAG, "Group detected: $title (pkg=$pkg)")
            prefs.addKnownGroup(title)
            prefs.setLastActiveGroup(title, System.currentTimeMillis())
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
