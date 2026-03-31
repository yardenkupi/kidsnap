package com.childfilter.app.unit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.childfilter.app.data.AppPreferences
import com.childfilter.app.data.ChildProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class AppPreferencesTest {

    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = AppPreferences(context)
    }

    // ── saveEmbedding / getEmbedding round-trip ──

    @Test
    fun `saveEmbedding and getEmbedding round-trip`() = runTest {
        val embedding = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
        prefs.saveEmbedding(embedding)
        val result = prefs.getEmbedding().first()
        assertNotNull(result)
        assertTrue(embedding.contentEquals(result!!))
    }

    @Test
    fun `getEmbedding returns null by default`() = runTest {
        val result = prefs.getEmbedding().first()
        assertNull(result)
    }

    // ── saveThreshold / getThreshold ──

    @Test
    fun `getThreshold returns default 0_75`() = runTest {
        val result = prefs.getThreshold().first()
        assertEquals(0.75f, result, 0.001f)
    }

    @Test
    fun `saveThreshold and getThreshold custom value`() = runTest {
        prefs.saveThreshold(0.9f)
        val result = prefs.getThreshold().first()
        assertEquals(0.9f, result, 0.001f)
    }

    // ── setServiceEnabled / isServiceEnabled ──

    @Test
    fun `isServiceEnabled returns false by default`() = runTest {
        val result = prefs.isServiceEnabled().first()
        assertFalse(result)
    }

    @Test
    fun `setServiceEnabled true then isServiceEnabled returns true`() = runTest {
        prefs.setServiceEnabled(true)
        assertTrue(prefs.isServiceEnabled().first())
    }

    @Test
    fun `setServiceEnabled false after true`() = runTest {
        prefs.setServiceEnabled(true)
        prefs.setServiceEnabled(false)
        assertFalse(prefs.isServiceEnabled().first())
    }

    // ── addKnownGroup / getKnownGroups ──

    @Test
    fun `getKnownGroups returns empty set by default`() = runTest {
        val result = prefs.getKnownGroups().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `addKnownGroup adds groups`() = runTest {
        prefs.addKnownGroup("Family Photos")
        prefs.addKnownGroup("School Group")
        val result = prefs.getKnownGroups().first()
        assertEquals(2, result.size)
        assertTrue(result.contains("Family Photos"))
        assertTrue(result.contains("School Group"))
    }

    @Test
    fun `addKnownGroup does not duplicate`() = runTest {
        prefs.addKnownGroup("Family Photos")
        prefs.addKnownGroup("Family Photos")
        val result = prefs.getKnownGroups().first()
        assertEquals(1, result.size)
    }

    // ── saveSelectedGroups / getSelectedGroups ──

    @Test
    fun `getSelectedGroups returns empty set by default`() = runTest {
        val result = prefs.getSelectedGroups().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `saveSelectedGroups and getSelectedGroups round-trip`() = runTest {
        val groups = setOf("Group A", "Group B", "Group C")
        prefs.saveSelectedGroups(groups)
        val result = prefs.getSelectedGroups().first()
        assertEquals(groups, result)
    }

    // ── saveChildren / getChildren serialization round-trip ──

    @Test
    fun `getChildren returns empty list by default`() = runTest {
        val result = prefs.getChildren().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `saveChildren and getChildren round-trip with ChildProfile`() = runTest {
        val children = listOf(
            ChildProfile(
                id = "child-1",
                name = "Alice",
                embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
                photoUri = "content://media/external/images/1"
            ),
            ChildProfile(
                id = "child-2",
                name = "Bob",
                embedding = floatArrayOf(0.4f, 0.5f, 0.6f),
                photoUri = null
            )
        )
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(2, result.size)
        assertEquals("child-1", result[0].id)
        assertEquals("Alice", result[0].name)
        assertTrue(floatArrayOf(0.1f, 0.2f, 0.3f).contentEquals(result[0].embedding))
        assertEquals("content://media/external/images/1", result[0].photoUri)
        assertEquals("child-2", result[1].id)
        assertEquals("Bob", result[1].name)
        assertTrue(floatArrayOf(0.4f, 0.5f, 0.6f).contentEquals(result[1].embedding))
        assertNull(result[1].photoUri)
    }

    // ── incrementProcessed / incrementMatched / getStats ──

    @Test
    fun `getStats returns zeros by default`() = runTest {
        val stats = prefs.getStats().first()
        assertEquals(0, stats.first)
        assertEquals(0, stats.second)
        assertEquals(0L, stats.third)
    }

    @Test
    fun `incrementProcessed increments processed count`() = runTest {
        prefs.incrementProcessed()
        prefs.incrementProcessed()
        prefs.incrementProcessed()
        val stats = prefs.getStats().first()
        assertEquals(3, stats.first)
        assertEquals(0, stats.second)
    }

    @Test
    fun `incrementMatched increments matched count and sets last match time`() = runTest {
        prefs.incrementMatched()
        val stats = prefs.getStats().first()
        assertEquals(0, stats.first)
        assertEquals(1, stats.second)
        assertTrue(stats.third > 0L)
    }

    // ── resetStats ──

    @Test
    fun `resetStats resets all stats to zero`() = runTest {
        prefs.incrementProcessed()
        prefs.incrementProcessed()
        prefs.incrementMatched()
        prefs.resetStats()
        val stats = prefs.getStats().first()
        assertEquals(0, stats.first)
        assertEquals(0, stats.second)
        assertEquals(0L, stats.third)
    }

    // ── addLogEntry / getActivityLog ──

    @Test
    fun `getActivityLog returns empty by default`() = runTest {
        val log = prefs.getActivityLog().first()
        assertTrue(log.isEmpty())
    }

    @Test
    fun `addLogEntry adds entries in newest-first order`() = runTest {
        prefs.addLogEntry("scan", "Scanned photo A")
        prefs.addLogEntry("match", "Matched photo B")
        val log = prefs.getActivityLog().first()
        assertEquals(2, log.size)
        // Newest first: "match" was added last so it should be first
        assertEquals("match", log[0].type)
        assertEquals("Matched photo B", log[0].details)
        assertEquals("scan", log[1].type)
        assertEquals("Scanned photo A", log[1].details)
    }

    @Test
    fun `addLogEntry enforces max 50 entries`() = runTest {
        for (i in 1..55) {
            prefs.addLogEntry("scan", "Entry $i")
        }
        val log = prefs.getActivityLog().first()
        assertEquals(50, log.size)
        // Newest entry should be first
        assertEquals("Entry 55", log[0].details)
    }

    // ── clearActivityLog ──

    @Test
    fun `clearActivityLog clears all entries`() = runTest {
        prefs.addLogEntry("scan", "Entry 1")
        prefs.addLogEntry("match", "Entry 2")
        prefs.clearActivityLog()
        val log = prefs.getActivityLog().first()
        assertTrue(log.isEmpty())
    }

    // ── setDarkMode / getDarkMode ──

    @Test
    fun `getDarkMode returns false by default`() = runTest {
        val result = prefs.getDarkMode().first()
        assertFalse(result)
    }

    @Test
    fun `setDarkMode true then getDarkMode returns true`() = runTest {
        prefs.setDarkMode(true)
        assertTrue(prefs.getDarkMode().first())
    }

    // ── setNotificationsEnabled / getNotificationsEnabled ──

    @Test
    fun `getNotificationsEnabled returns true by default`() = runTest {
        val result = prefs.getNotificationsEnabled().first()
        assertTrue(result)
    }

    @Test
    fun `setNotificationsEnabled false then getNotificationsEnabled returns false`() = runTest {
        prefs.setNotificationsEnabled(false)
        assertFalse(prefs.getNotificationsEnabled().first())
    }
}
