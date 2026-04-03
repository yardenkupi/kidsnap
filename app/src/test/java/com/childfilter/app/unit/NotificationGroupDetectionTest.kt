package com.childfilter.app.unit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the group-detection heuristic from NotificationWatcherService.processNotification().
 *
 * Mirrors the four-signal detection logic:
 *   1. android.isGroupConversation (MessagingStyle flag — covers muted/silent groups)
 *   2. text.contains(": ")         (classic "Sender: message" format)
 *   3. android.subText != null     (bundled/summary notifications)
 *   4. text matches "N message(s)" (silent-group summary format)
 *
 * Also tests package-filtering: only "com.whatsapp" and "com.whatsapp.w4b" are accepted.
 */
class NotificationGroupDetectionTest {

    // ── Mirrors NotificationWatcherService logic ──

    private val WHATSAPP_PACKAGE = "com.whatsapp"
    private val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    private fun isAcceptedPackage(pkg: String): Boolean =
        pkg == WHATSAPP_PACKAGE || pkg == WHATSAPP_BUSINESS_PACKAGE

    private fun isGroupMessage(
        text: String,
        hasSubText: Boolean = false,
        isGroupConversation: Boolean = false
    ): Boolean {
        val hasSilentSummaryFormat = text.matches(Regex("""\d+\s+new\s+messages?""", RegexOption.IGNORE_CASE)) ||
            text.matches(Regex("""\d+\s+messages?""", RegexOption.IGNORE_CASE))
        return isGroupConversation || text.contains(": ") || hasSubText || hasSilentSummaryFormat
    }

    // ── Package filtering ──

    @Test
    fun `com_whatsapp package is accepted`() {
        assertTrue(isAcceptedPackage("com.whatsapp"))
    }

    @Test
    fun `com_whatsapp_w4b package is accepted`() {
        assertTrue(isAcceptedPackage("com.whatsapp.w4b"))
    }

    @Test
    fun `telegram package is rejected`() {
        assertFalse(isAcceptedPackage("org.telegram.messenger"))
    }

    @Test
    fun `signal package is rejected`() {
        assertFalse(isAcceptedPackage("org.thoughtcrime.securesms"))
    }

    @Test
    fun `empty package string is rejected`() {
        assertFalse(isAcceptedPackage(""))
    }

    @Test
    fun `com_whatsapp_sub_package is rejected (no prefix matching)`() {
        assertFalse(isAcceptedPackage("com.whatsapp.subpackage"))
    }

    @Test
    fun `com_whatsapp_w4b_extra is rejected (exact match only)`() {
        assertFalse(isAcceptedPackage("com.whatsapp.w4b.extra"))
    }

    @Test
    fun `WHATSAPP uppercase is rejected (case-sensitive)`() {
        assertFalse(isAcceptedPackage("com.WhatsApp"))
    }

    // ── Group detection: text.contains(": ") ──

    @Test
    fun `text with sender-colon-space pattern detected as group`() {
        assertTrue(isGroupMessage("John: Hello everyone", false))
    }

    @Test
    fun `text with colon-space at start detected as group`() {
        assertTrue(isGroupMessage(": hello", false))
    }

    @Test
    fun `text with colon-space at end detected as group`() {
        assertTrue(isGroupMessage("User: ", false))
    }

    @Test
    fun `text with only colon-space is group`() {
        assertTrue(isGroupMessage(": ", false))
    }

    @Test
    fun `text with multiple colon-space sequences detected as group`() {
        assertTrue(isGroupMessage("Alice: Bob: test", false))
    }

    @Test
    fun `text with colon but no following space is NOT group`() {
        assertFalse(isGroupMessage("http://example.com", false))
    }

    @Test
    fun `text with colon-slash-slash is NOT group`() {
        assertFalse(isGroupMessage("visit https://link.com now", false))
    }

    @Test
    fun `empty text without subText is NOT group`() {
        assertFalse(isGroupMessage("", false))
    }

    @Test
    fun `plain message without colon-space is NOT group`() {
        assertFalse(isGroupMessage("Hey! How are you?", false))
    }

    @Test
    fun `message with colon but space before (not after) is NOT group`() {
        assertFalse(isGroupMessage("meeting at 5 :30pm", false))
    }

    @Test
    fun `message with colon then newline is NOT group (needs space)`() {
        assertFalse(isGroupMessage("item:\ndetails", false))
    }

    @Test
    fun `message with time like 3h00 is NOT group`() {
        assertFalse(isGroupMessage("Meet at 3:00pm", false))
    }

    @Test
    fun `URL-only message is NOT group`() {
        assertFalse(isGroupMessage("https://example.com/page", false))
    }

    @Test
    fun `mixed URL and sender pattern is detected as group`() {
        // "Alice: https://example.com" — contains ": " after Alice
        assertTrue(isGroupMessage("Alice: https://example.com", false))
    }

    // ── Group detection: subText (bundled notifications) ──

    @Test
    fun `empty text with subText detected as group`() {
        assertTrue(isGroupMessage("", true))
    }

    @Test
    fun `plain text with subText detected as group`() {
        assertTrue(isGroupMessage("Hey!", true))
    }

    @Test
    fun `URL-only text with subText detected as group`() {
        assertTrue(isGroupMessage("https://example.com", true))
    }

    @Test
    fun `subText alone (empty text) triggers group detection`() {
        assertTrue(isGroupMessage("", hasSubText = true))
    }

    @Test
    fun `no colon-space and no subText is NOT group`() {
        assertFalse(isGroupMessage("Good morning", false))
    }

    // ── Both conditions true ──

    @Test
    fun `text with colon-space AND subText is group (both true)`() {
        assertTrue(isGroupMessage("Alice: Hi", true))
    }

    // ── WhatsApp group title characteristics ──

    @Test
    fun `group name with emoji in title text`() {
        // The title field is not tested here (it's stored directly), but verify text parsing
        assertTrue(isGroupMessage("👶 Baby Alice: How cute!", false))
    }

    @Test
    fun `group name with Unicode sender name and colon-space`() {
        assertTrue(isGroupMessage("محمد: مرحبا", false))
    }

    @Test
    fun `long message body containing colon-space is detected as group`() {
        val longMsg = "Alice: " + "x".repeat(1000)
        assertTrue(isGroupMessage(longMsg, false))
    }

    @Test
    fun `message with colon-space only in middle is detected as group`() {
        assertTrue(isGroupMessage("someone in the group said: hello there", false))
    }

    // ── Combined package + group detection logic ──

    @Test
    fun `whatsapp package with group text fully processes`() {
        val pkg = "com.whatsapp"
        val text = "Alice: Hello"
        val hasSubText = false
        assertTrue(isAcceptedPackage(pkg) && isGroupMessage(text, hasSubText))
    }

    @Test
    fun `whatsapp business package with subText group fully processes`() {
        val pkg = "com.whatsapp.w4b"
        val text = "Hello"
        val hasSubText = true
        assertTrue(isAcceptedPackage(pkg) && isGroupMessage(text, hasSubText))
    }

    @Test
    fun `wrong package even with group text does not process`() {
        val pkg = "com.telegram"
        val text = "Alice: Hello"
        assertFalse(isAcceptedPackage(pkg) && isGroupMessage(text, false))
    }

    @Test
    fun `correct package with personal message does not detect group`() {
        val pkg = "com.whatsapp"
        val text = "How are you doing today?"
        val hasSubText = false
        assertTrue(isAcceptedPackage(pkg))
        assertFalse(isGroupMessage(text, hasSubText))
    }

    // ── Silent / muted group detection (android.isGroupConversation) ──

    @Test
    fun `isGroupConversation true with no colon-space detected as group (muted group)`() {
        assertTrue(isGroupMessage("5 new messages", isGroupConversation = true))
    }

    @Test
    fun `isGroupConversation true with empty text still detected as group`() {
        assertTrue(isGroupMessage("", isGroupConversation = true))
    }

    @Test
    fun `isGroupConversation true with plain personal-looking text detected as group`() {
        assertTrue(isGroupMessage("How are you?", isGroupConversation = true))
    }

    @Test
    fun `isGroupConversation false and no other signals is NOT group`() {
        assertFalse(isGroupMessage("How are you?", hasSubText = false, isGroupConversation = false))
    }

    // ── Silent-summary format ("N messages" / "N new messages") ──

    @Test
    fun `text 1 message matches silent summary format`() {
        assertTrue(isGroupMessage("1 message"))
    }

    @Test
    fun `text 5 messages matches silent summary format`() {
        assertTrue(isGroupMessage("5 messages"))
    }

    @Test
    fun `text 1 new message matches silent summary format`() {
        assertTrue(isGroupMessage("1 new message"))
    }

    @Test
    fun `text 12 new messages matches silent summary format`() {
        assertTrue(isGroupMessage("12 new messages"))
    }

    @Test
    fun `text 100 new messages matches silent summary format`() {
        assertTrue(isGroupMessage("100 new messages"))
    }

    @Test
    fun `silent summary format is case-insensitive`() {
        assertTrue(isGroupMessage("3 New Messages"))
        assertTrue(isGroupMessage("3 NEW MESSAGES"))
    }

    @Test
    fun `text messages without leading digit is NOT silent summary format`() {
        // "messages" alone without a number should not match
        assertFalse(isGroupMessage("messages"))
    }

    @Test
    fun `text new messages without digit is NOT silent summary format`() {
        assertFalse(isGroupMessage("new messages"))
    }

    @Test
    fun `text with digit but not messages suffix is NOT silent summary format`() {
        assertFalse(isGroupMessage("5 photos"))
        assertFalse(isGroupMessage("3 notifications"))
    }

    @Test
    fun `silent summary with isGroupConversation false still detected`() {
        // The N-messages pattern alone is enough
        assertTrue(isGroupMessage("7 new messages", isGroupConversation = false))
    }

    @Test
    fun `all four signals independently detect group`() {
        // 1. isGroupConversation alone
        assertTrue(isGroupMessage("hello", isGroupConversation = true))
        // 2. colon-space format alone
        assertTrue(isGroupMessage("Alice: hello"))
        // 3. subText alone
        assertTrue(isGroupMessage("hello", hasSubText = true))
        // 4. silent summary format alone
        assertTrue(isGroupMessage("3 messages"))
    }

    @Test
    fun `muted group with zero signals is NOT detected`() {
        // No signals: plain personal message, not group conversation
        assertFalse(isGroupMessage("OK see you later", hasSubText = false, isGroupConversation = false))
    }
}
