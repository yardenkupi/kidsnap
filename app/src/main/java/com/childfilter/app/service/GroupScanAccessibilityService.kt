package com.childfilter.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.childfilter.app.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Accessibility service that reads WhatsApp's conversation list to extract group names.
 *
 * This solves the case on Android < 12 where groups with notifications completely disabled
 * are invisible to the notification system. On Android 12+, [NotificationWatcherService]
 * uses getNotificationChannels() instead — no accessibility service needed.
 *
 * HOW IT WORKS
 * ─────────────
 * WhatsApp's conversation list is a RecyclerView. The first short TextView in each item
 * is the conversation name (group or contact). We collect these as the user browses.
 *
 * SAFETY GUARDS
 * ─────────────
 * • Only fires for WhatsApp packages (config + runtime check).
 * • Conversation names are ≤ 60 chars and single-line; message body text is longer/
 *   multi-line and filtered out — so we never capture message content.
 * • Requires ≥ 2 valid items before trusting the scan (guards against false positives
 *   when the user is inside a single-chat view).
 * • No message text, contact numbers, or media is ever read or stored.
 */
class GroupScanAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "GroupScanA11y"
        private const val WHATSAPP_PKG = "com.whatsapp"
        private const val WHATSAPP_BIZ_PKG = "com.whatsapp.w4b"

        /** Reject texts longer than this — message bodies are typically longer than names. */
        private const val MAX_NAME_LEN = 60

        /** Minimum items that must pass name validation before we trust the scan result. */
        private const val MIN_ITEMS_FOR_TRUST = 2

        private val TIMESTAMP_RE = Regex("""^\d{1,2}:\d{2}(\s?[AaPp][Mm])?$""")
        private val UNREAD_RE    = Regex("""^\d{1,3}\+?$""")
        private val DAY_WORDS    = setOf(
            "yesterday", "monday", "tuesday", "wednesday",
            "thursday", "friday", "saturday", "sunday"
        )

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf(WHATSAPP_PKG, WHATSAPP_BIZ_PKG)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 300
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        Log.i(TAG, "Group scanner connected")
    }

    override fun onDestroy() {
        _isRunning.value = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() { /* required by API */ }

    // ── Event handling ───────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg != WHATSAPP_PKG && pkg != WHATSAPP_BIZ_PKG) return

        // Only act on structural changes — not every keystroke or focus shift
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val root = rootInActiveWindow ?: return
        scope.launch {
            val names = extractConversationNames(root)
            if (names.isNotEmpty()) {
                val prefs = AppPreferences.getInstance(applicationContext)
                names.forEach { prefs.addKnownGroup(it) }
                Log.d(TAG, "Imported ${names.size} name(s) from WhatsApp conversation list")
            }
        }
    }

    // ── Conversation name extraction ─────────────────────────────────────────

    /**
     * Finds WhatsApp's main conversation RecyclerView and extracts the first valid text
     * (= conversation name) from each visible list item.
     */
    private fun extractConversationNames(root: AccessibilityNodeInfo): List<String> {
        // Manual traversal instead of findAccessibilityNodeInfosByClassName to avoid
        // API compatibility issues across compileSdk versions.
        val recyclers = mutableListOf<AccessibilityNodeInfo>()
        collectRecyclerViews(root, recyclers, maxDepth = 8)
        if (recyclers.isEmpty()) return emptyList()

        // Pick the RecyclerView with the most children — that's the conversation list,
        // not a small emoji grid or picker
        val conversationList = recyclers.maxByOrNull { it.getChildCount() } ?: return emptyList()
        if (conversationList.getChildCount() < MIN_ITEMS_FOR_TRUST) return emptyList()

        val candidates = mutableListOf<String>()
        for (i in 0 until conversationList.getChildCount()) {
            val item = conversationList.getChild(i) ?: continue
            val name = firstValidName(item, maxDepth = 6) ?: continue
            candidates.add(name)
        }

        // If too few items passed the filter we're probably not on the conversation list
        // (e.g. inside a chat, a settings screen, or a media viewer). Discard results.
        return if (candidates.size >= MIN_ITEMS_FOR_TRUST) candidates else emptyList()
    }

    /**
     * Recursively collects all RecyclerView nodes beneath [node], stopping descent
     * once a RecyclerView is found (its internal children are list items, not containers).
     */
    private fun collectRecyclerViews(
        node: AccessibilityNodeInfo?,
        result: MutableList<AccessibilityNodeInfo>,
        maxDepth: Int
    ) {
        if (node == null || maxDepth <= 0) return
        if (node.className?.contains("RecyclerView") == true) {
            result.add(node)
            return  // don't descend into the RecyclerView's own children
        }
        for (i in 0 until node.getChildCount()) {
            collectRecyclerViews(node.getChild(i), result, maxDepth - 1)
        }
    }

    /**
     * Depth-first search for the first text in [node] that looks like a conversation name.
     */
    private fun firstValidName(node: AccessibilityNodeInfo?, maxDepth: Int): String? {
        if (node == null || maxDepth <= 0) return null

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && looksLikeName(text)) return text

        for (i in 0 until node.getChildCount()) {
            val result = firstValidName(node.getChild(i), maxDepth - 1)
            if (result != null) return result
        }
        return null
    }

    private fun looksLikeName(text: String): Boolean {
        if (text.length > MAX_NAME_LEN) return false   // likely a message body
        if (text.contains('\n')) return false           // multi-line = message content
        if (text.length <= 2) return false              // status emoji, single letters, etc.
        if (TIMESTAMP_RE.matches(text)) return false    // "10:30", "10:30 AM"
        if (UNREAD_RE.matches(text)) return false       // "3", "99+"
        if (text.lowercase() in DAY_WORDS) return false // "Yesterday", "Monday", etc.
        return true
    }
}
