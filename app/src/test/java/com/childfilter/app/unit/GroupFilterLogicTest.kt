package com.childfilter.app.unit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.childfilter.app.data.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the group-window filtering logic that lives in FolderWatcherService.processImage().
 *
 * The logic is:
 *   if (selectedGroups.isNotEmpty()) {
 *     val (lastGroup, lastTime) = getLastActiveGroup()
 *     val timeDiff = now - lastTime
 *     if (!selectedGroups.contains(lastGroup) || timeDiff > GROUP_WINDOW_MS) return  // skip
 *   }
 *
 * We test this by setting up AppPreferences state and asserting whether shouldProcess() returns
 * true or false using the same predicate extracted here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GroupFilterLogicTest {

    private lateinit var prefs: AppPreferences

    private val GROUP_WINDOW_MS = 300_000L  // 5 minutes — must match FolderWatcherService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = AppPreferences(context)
        runBlocking { prefs.clearAllForTest() }
    }

    /**
     * Mirrors the filtering predicate from FolderWatcherService.processImage().
     * Returns true if the image should be processed (i.e., it passes the filter).
     */
    private suspend fun shouldProcess(now: Long = System.currentTimeMillis()): Boolean {
        val selectedGroups = prefs.getSelectedGroups().first()
        if (selectedGroups.isEmpty()) return true  // no filter active

        val (lastGroup, lastTime) = prefs.getLastActiveGroup().first()
        val timeDiff = now - lastTime
        return selectedGroups.contains(lastGroup) && timeDiff <= GROUP_WINDOW_MS
    }

    // ── No selected groups → bypass filter ──

    @Test
    fun `no selected groups - always processes (filter bypass)`() = runTest {
        assertTrue(shouldProcess())
    }

    @Test
    fun `no selected groups even with recent active group - processes`() = runTest {
        prefs.setLastActiveGroup("Family Group", System.currentTimeMillis())
        assertTrue(shouldProcess())
    }

    // ── Group in selected groups + within window ──

    @Test
    fun `selected group is last active and within window - processes`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Family Photos"))
        prefs.setLastActiveGroup("Family Photos", now - 1000L) // 1 second ago
        assertTrue(shouldProcess(now))
    }

    @Test
    fun `selected group active just 1ms ago - processes`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Family Photos"))
        prefs.setLastActiveGroup("Family Photos", now - 1L)
        assertTrue(shouldProcess(now))
    }

    @Test
    fun `selected group active exactly at window boundary (300000ms) - processes`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Family Photos"))
        prefs.setLastActiveGroup("Family Photos", now - GROUP_WINDOW_MS)
        assertTrue(shouldProcess(now))
    }

    @Test
    fun `selected group active 4m59s ago (within 5min window) - processes`() = runTest {
        val now = System.currentTimeMillis()
        val fourMin59Sec = 4 * 60 * 1000L + 59 * 1000L
        prefs.saveSelectedGroups(setOf("School Group"))
        prefs.setLastActiveGroup("School Group", now - fourMin59Sec)
        assertTrue(shouldProcess(now))
    }

    // ── Group in selected groups + outside window → skip ──

    @Test
    fun `selected group active 1ms past window (300001ms) - skips`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Family Photos"))
        prefs.setLastActiveGroup("Family Photos", now - (GROUP_WINDOW_MS + 1L))
        assertFalse(shouldProcess(now))
    }

    @Test
    fun `selected group active 6 minutes ago - skips`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Family Photos"))
        prefs.setLastActiveGroup("Family Photos", now - 6 * 60 * 1000L)
        assertFalse(shouldProcess(now))
    }

    @Test
    fun `selected group active 1 hour ago - skips`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Family Photos"))
        prefs.setLastActiveGroup("Family Photos", now - 60 * 60 * 1000L)
        assertFalse(shouldProcess(now))
    }

    @Test
    fun `selected group with lastTime 0L (never set) - skips`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Family Photos"))
        // Default lastTime is 0L → timeDiff = now - 0 = very large → outside window
        assertFalse(shouldProcess(now))
    }

    // ── Group NOT in selected groups → skip ──

    @Test
    fun `different group was last active within window - skips`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Family Photos"))
        prefs.setLastActiveGroup("School Group", now - 1000L) // within window, wrong group
        assertFalse(shouldProcess(now))
    }

    @Test
    fun `last active group is empty string when groups selected - skips`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Family Photos"))
        // Don't set any last active group → default is ("", 0L)
        assertFalse(shouldProcess(now))
    }

    @Test
    fun `two groups selected, third was last active - skips`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Group A", "Group B"))
        prefs.setLastActiveGroup("Group C", now - 1000L)
        assertFalse(shouldProcess(now))
    }

    // ── Multiple selected groups ──

    @Test
    fun `two groups selected, first was last active within window - processes`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Group A", "Group B"))
        prefs.setLastActiveGroup("Group A", now - 1000L)
        assertTrue(shouldProcess(now))
    }

    @Test
    fun `two groups selected, second was last active within window - processes`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Group A", "Group B"))
        prefs.setLastActiveGroup("Group B", now - 1000L)
        assertTrue(shouldProcess(now))
    }

    @Test
    fun `ten groups selected, last active is one of them - processes`() = runTest {
        val now = System.currentTimeMillis()
        val groups = (1..10).map { "Group $it" }.toSet()
        prefs.saveSelectedGroups(groups)
        prefs.setLastActiveGroup("Group 7", now - 5000L)
        assertTrue(shouldProcess(now))
    }

    @Test
    fun `ten groups selected, last active is not one of them - skips`() = runTest {
        val now = System.currentTimeMillis()
        val groups = (1..10).map { "Group $it" }.toSet()
        prefs.saveSelectedGroups(groups)
        prefs.setLastActiveGroup("Group 11", now - 5000L)
        assertFalse(shouldProcess(now))
    }

    // ── State transitions ──

    @Test
    fun `changing selection from group A to group B - old match no longer passes`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Group A"))
        prefs.setLastActiveGroup("Group A", now - 1000L)
        assertTrue("Should process with Group A selected", shouldProcess(now))

        // Switch selection to Group B
        prefs.saveSelectedGroups(setOf("Group B"))
        assertFalse("Should not process when Group A is no longer selected", shouldProcess(now))
    }

    @Test
    fun `clearing selected groups makes filter bypass work again`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Group A"))
        prefs.setLastActiveGroup("Group B", now - 1000L)
        assertFalse("Should not process with wrong group active", shouldProcess(now))

        prefs.saveSelectedGroups(emptySet())
        assertTrue("Should process after clearing selection (filter bypass)", shouldProcess(now))
    }

    @Test
    fun `group window resets when new notification arrives for same group`() = runTest {
        val t1 = 1_000_000L
        val t2 = t1 + 6 * 60 * 1000L  // 6 minutes after t1
        prefs.saveSelectedGroups(setOf("Family Photos"))

        // At t1, group is active
        prefs.setLastActiveGroup("Family Photos", t1)
        assertFalse("Should skip at t1+6min (outside window)", shouldProcess(t2))

        // New notification arrives at t2
        prefs.setLastActiveGroup("Family Photos", t2)
        val t3 = t2 + 1000L  // 1 second after the new notification
        assertTrue("Should process at t2+1s (window reset by new notification)", shouldProcess(t3))
    }

    @Test
    fun `Unicode group name matching works correctly`() = runTest {
        val now = System.currentTimeMillis()
        val groupName = "👨‍👩‍👧 Family ❤️"
        prefs.saveSelectedGroups(setOf(groupName))
        prefs.setLastActiveGroup(groupName, now - 1000L)
        assertTrue(shouldProcess(now))
    }

    @Test
    fun `group name matching is case-sensitive`() = runTest {
        val now = System.currentTimeMillis()
        prefs.saveSelectedGroups(setOf("Family Photos")) // capital F and P
        prefs.setLastActiveGroup("family photos", now - 1000L) // lowercase
        assertFalse("Case-sensitive group name matching should fail", shouldProcess(now))
    }
}
