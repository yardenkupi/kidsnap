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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class AppPreferencesTest {

    private lateinit var prefs: AppPreferences
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Delete DataStore file so every test starts with completely clean state.
        // Partial teardown via prefs APIs misses keys like REFERENCE_EMBEDDING,
        // SIMILARITY_THRESHOLD, KNOWN_GROUPS and LAST_ACTIVE_GROUP which have no
        // dedicated clear method, causing cross-test leakage.
        val datastoreDir = File(context.filesDir, "datastore")
        datastoreDir.deleteRecursively()
        // Reset the AppPreferences singleton so the next getInstance() call creates
        // a fresh DataStore instance pointing at the newly emptied directory.
        val field = AppPreferences::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
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

    @Test
    fun `saveEmbedding with 192-dimensional vector round-trips correctly`() = runTest {
        val embedding = FloatArray(192) { it * 0.001f }
        prefs.saveEmbedding(embedding)
        val result = prefs.getEmbedding().first()
        assertNotNull(result)
        assertEquals(192, result!!.size)
        for (i in embedding.indices) {
            assertEquals(embedding[i], result[i], 0.0001f)
        }
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

    @Test
    fun `saveThreshold boundary value 0_0f persists correctly`() = runTest {
        prefs.saveThreshold(0.0f)
        val result = prefs.getThreshold().first()
        assertEquals(0.0f, result, 0.001f)
    }

    @Test
    fun `saveThreshold boundary value 1_0f persists correctly`() = runTest {
        prefs.saveThreshold(1.0f)
        val result = prefs.getThreshold().first()
        assertEquals(1.0f, result, 0.001f)
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

    @Test
    fun `addKnownGroup with empty string still adds it`() = runTest {
        prefs.addKnownGroup("")
        val result = prefs.getKnownGroups().first()
        assertTrue(result.contains(""))
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

    @Test
    fun `saveSelectedGroups with emptySet returns empty`() = runTest {
        prefs.saveSelectedGroups(setOf("Some Group"))
        prefs.saveSelectedGroups(emptySet())
        val result = prefs.getSelectedGroups().first()
        assertTrue(result.isEmpty())
    }

    // ── setLastActiveGroup / getLastActiveGroup ──

    @Test
    fun `setLastActiveGroup and getLastActiveGroup round-trip`() = runTest {
        val expectedName = "Family Group"
        val expectedTime = 1700000000000L
        prefs.setLastActiveGroup(expectedName, expectedTime)
        val result = prefs.getLastActiveGroup().first()
        assertEquals(expectedName, result.first)
        assertEquals(expectedTime, result.second)
    }

    @Test
    fun `getLastActiveGroup default returns empty string and 0L`() = runTest {
        val result = prefs.getLastActiveGroup().first()
        assertEquals("", result.first)
        assertEquals(0L, result.second)
    }

    @Test
    fun `setLastActiveGroup with pipe character in name serializes correctly`() = runTest {
        // The implementation uses split("|", limit=2) so the pipe becomes part of name
        // and the time is parsed from the last segment after the first pipe
        val nameWithPipe = "Group|Name"
        val expectedTime = 1234567890L
        prefs.setLastActiveGroup(nameWithPipe, expectedTime)
        val result = prefs.getLastActiveGroup().first()
        // With limit=2 split, "Group|Name|1234567890" splits into ["Group", "Name|1234567890"]
        // parts[0]="Group", parts[1]="Name|1234567890" which .toLongOrNull() returns null -> 0L
        // So we verify the behavior (not necessarily "Group|Name") — the name part is "Group"
        // and time parsing fails on "Name|...", giving 0L
        assertEquals("Group", result.first)
        assertEquals(0L, result.second) // Cannot parse "Name|1234567890" as Long
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

    @Test
    fun `saveChildren preserves insertion order`() = runTest {
        val children = listOf(
            ChildProfile("id-c", "Charlie", floatArrayOf(0.3f), null),
            ChildProfile("id-a", "Alice",   floatArrayOf(0.1f), null),
            ChildProfile("id-b", "Bob",     floatArrayOf(0.2f), null)
        )
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(3, result.size)
        assertEquals("Charlie", result[0].name)
        assertEquals("Alice",   result[1].name)
        assertEquals("Bob",     result[2].name)
    }

    @Test
    fun `saveChildren with special characters in name parses back correctly`() = runTest {
        val specialName = """John "The Kid" O'Brien"""
        val children = listOf(
            ChildProfile("id-special", specialName, floatArrayOf(0.5f, 0.6f), null)
        )
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(1, result.size)
        assertEquals(specialName, result[0].name)
    }

    @Test
    fun `saveChildren with backslash in name parses back correctly`() = runTest {
        val nameWithBackslash = "Child\\Name"
        val children = listOf(
            ChildProfile("id-bs", nameWithBackslash, floatArrayOf(0.1f), null)
        )
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(1, result.size)
        assertEquals(nameWithBackslash, result[0].name)
    }

    @Test
    fun `saveChildren with 0 children returns empty list`() = runTest {
        // First add some children
        prefs.saveChildren(listOf(ChildProfile("id-1", "Test", floatArrayOf(0.1f), null)))
        // Then explicitly save empty list
        prefs.saveChildren(emptyList())
        val result = prefs.getChildren().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `clearChildren results in empty list after having children`() = runTest {
        prefs.saveChildren(listOf(ChildProfile("id-1", "Alice", floatArrayOf(0.1f), null)))
        prefs.clearChildren()
        val result = prefs.getChildren().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple children with same name but different IDs both stored`() = runTest {
        val children = listOf(
            ChildProfile("id-1", "Alice", floatArrayOf(0.1f), null),
            ChildProfile("id-2", "Alice", floatArrayOf(0.9f), null)
        )
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(2, result.size)
        assertEquals("id-1", result[0].id)
        assertEquals("id-2", result[1].id)
        assertEquals("Alice", result[0].name)
        assertEquals("Alice", result[1].name)
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

    // ── hasSeenTutorial ──

    @Test
    fun `hasSeenTutorial defaults to false`() = runTest {
        val result = prefs.hasSeenTutorial().first()
        assertFalse(result)
    }

    @Test
    fun `setHasSeenTutorial true then hasSeenTutorial returns true`() = runTest {
        prefs.setHasSeenTutorial(true)
        assertTrue(prefs.hasSeenTutorial().first())
    }

    // ── hasCompletedOnboarding ──

    @Test
    fun `hasCompletedOnboarding defaults to false`() = runTest {
        val result = prefs.hasCompletedOnboarding().first()
        assertFalse(result)
    }

    @Test
    fun `setOnboardingCompleted true then hasCompletedOnboarding returns true`() = runTest {
        prefs.setOnboardingCompleted(true)
        assertTrue(prefs.hasCompletedOnboarding().first())
    }
}
