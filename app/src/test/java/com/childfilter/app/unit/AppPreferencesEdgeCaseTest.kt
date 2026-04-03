package com.childfilter.app.unit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.childfilter.app.data.AppPreferences
import com.childfilter.app.data.ChildProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class AppPreferencesEdgeCaseTest {

    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = AppPreferences(context)
        runBlocking { prefs.clearAllForTest() }
    }

    // ── Embedding edge cases ──

    @Test
    fun `saveEmbedding with single-element vector round-trips correctly`() = runTest {
        val embedding = floatArrayOf(0.999f)
        prefs.saveEmbedding(embedding)
        val result = prefs.getEmbedding().first()
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals(0.999f, result[0], 0.0001f)
    }

    @Test
    fun `saveEmbedding with 512-dimensional vector round-trips correctly`() = runTest {
        val embedding = FloatArray(512) { kotlin.math.sin(it.toDouble()).toFloat() }
        prefs.saveEmbedding(embedding)
        val result = prefs.getEmbedding().first()
        assertNotNull(result)
        assertEquals(512, result!!.size)
        for (i in embedding.indices) {
            assertEquals("Mismatch at index $i", embedding[i], result[i], 0.0001f)
        }
    }

    @Test
    fun `saveEmbedding with all-negative values round-trips correctly`() = runTest {
        val embedding = floatArrayOf(-0.5f, -0.3f, -0.8f, -0.1f)
        prefs.saveEmbedding(embedding)
        val result = prefs.getEmbedding().first()
        assertNotNull(result)
        assertTrue(embedding.contentEquals(result!!))
    }

    @Test
    fun `saveEmbedding with zero vector round-trips correctly`() = runTest {
        val embedding = FloatArray(128) { 0f }
        prefs.saveEmbedding(embedding)
        val result = prefs.getEmbedding().first()
        assertNotNull(result)
        assertEquals(128, result!!.size)
        for (v in result) assertEquals(0f, v, 0.0001f)
    }

    @Test
    fun `saveEmbedding overwrites previous embedding`() = runTest {
        val first = floatArrayOf(0.1f, 0.2f, 0.3f)
        val second = floatArrayOf(0.9f, 0.8f, 0.7f)
        prefs.saveEmbedding(first)
        prefs.saveEmbedding(second)
        val result = prefs.getEmbedding().first()
        assertNotNull(result)
        assertTrue(second.contentEquals(result!!))
    }

    @Test
    fun `saveEmbedding with very small float values preserves precision`() = runTest {
        val embedding = floatArrayOf(1e-7f, 1e-8f, 1e-9f)
        prefs.saveEmbedding(embedding)
        val result = prefs.getEmbedding().first()
        assertNotNull(result)
        // Float precision at this scale — allow relative tolerance
        for (i in embedding.indices) {
            assertEquals(embedding[i], result!![i], embedding[i] * 0.01f + 1e-10f)
        }
    }

    // ── Known groups edge cases ──

    @Test
    fun `addKnownGroup with Unicode group name stores correctly`() = runTest {
        val arabicName = "عائلة الأطفال"
        val emojiName = "👨‍👩‍👧‍👦 Family"
        prefs.addKnownGroup(arabicName)
        prefs.addKnownGroup(emojiName)
        val result = prefs.getKnownGroups().first()
        assertTrue(result.contains(arabicName))
        assertTrue(result.contains(emojiName))
    }

    @Test
    fun `addKnownGroup with 100 unique groups stores all`() = runTest {
        val groupNames = (1..100).map { "Group $it" }
        for (name in groupNames) {
            prefs.addKnownGroup(name)
        }
        val result = prefs.getKnownGroups().first()
        assertEquals(100, result.size)
        for (name in groupNames) {
            assertTrue("Missing: $name", result.contains(name))
        }
    }

    @Test
    fun `addKnownGroup with very long name (500 chars) stores correctly`() = runTest {
        val longName = "A".repeat(500)
        prefs.addKnownGroup(longName)
        val result = prefs.getKnownGroups().first()
        assertTrue(result.contains(longName))
    }

    @Test
    fun `addKnownGroup case-sensitive - same name different case stored as two groups`() = runTest {
        prefs.addKnownGroup("Family")
        prefs.addKnownGroup("family")
        prefs.addKnownGroup("FAMILY")
        val result = prefs.getKnownGroups().first()
        assertEquals(3, result.size)
        assertTrue(result.contains("Family"))
        assertTrue(result.contains("family"))
        assertTrue(result.contains("FAMILY"))
    }

    @Test
    fun `addKnownGroup with whitespace-only name stores it`() = runTest {
        prefs.addKnownGroup("   ")
        val result = prefs.getKnownGroups().first()
        assertTrue(result.contains("   "))
    }

    @Test
    fun `addKnownGroup with newline in name stores it`() = runTest {
        val nameWithNewline = "Group\nName"
        prefs.addKnownGroup(nameWithNewline)
        val result = prefs.getKnownGroups().first()
        assertTrue(result.contains(nameWithNewline))
    }

    // ── Selected groups edge cases ──

    @Test
    fun `saveSelectedGroups with 50 groups round-trips correctly`() = runTest {
        val groups = (1..50).map { "Group $it" }.toSet()
        prefs.saveSelectedGroups(groups)
        val result = prefs.getSelectedGroups().first()
        assertEquals(50, result.size)
        assertEquals(groups, result)
    }

    @Test
    fun `saveSelectedGroups subset of known groups works`() = runTest {
        prefs.addKnownGroup("Group A")
        prefs.addKnownGroup("Group B")
        prefs.addKnownGroup("Group C")
        prefs.saveSelectedGroups(setOf("Group A", "Group C"))
        val result = prefs.getSelectedGroups().first()
        assertEquals(2, result.size)
        assertTrue(result.contains("Group A"))
        assertFalse(result.contains("Group B"))
        assertTrue(result.contains("Group C"))
    }

    @Test
    fun `saveSelectedGroups replaces previous selection entirely`() = runTest {
        prefs.saveSelectedGroups(setOf("Group A", "Group B"))
        prefs.saveSelectedGroups(setOf("Group C"))
        val result = prefs.getSelectedGroups().first()
        assertEquals(1, result.size)
        assertTrue(result.contains("Group C"))
        assertFalse(result.contains("Group A"))
    }

    // ── Last active group edge cases ──

    @Test
    fun `setLastActiveGroup with zero timestamp round-trips correctly`() = runTest {
        prefs.setLastActiveGroup("Group X", 0L)
        val result = prefs.getLastActiveGroup().first()
        assertEquals("Group X", result.first)
        assertEquals(0L, result.second)
    }

    @Test
    fun `setLastActiveGroup with max Long value round-trips correctly`() = runTest {
        prefs.setLastActiveGroup("Group X", Long.MAX_VALUE)
        val result = prefs.getLastActiveGroup().first()
        assertEquals("Group X", result.first)
        assertEquals(Long.MAX_VALUE, result.second)
    }

    @Test
    fun `setLastActiveGroup overwrites previous active group`() = runTest {
        prefs.setLastActiveGroup("Group A", 1000L)
        prefs.setLastActiveGroup("Group B", 2000L)
        val result = prefs.getLastActiveGroup().first()
        assertEquals("Group B", result.first)
        assertEquals(2000L, result.second)
    }

    @Test
    fun `getLastActiveGroup when nothing set returns empty string and 0L`() = runTest {
        val result = prefs.getLastActiveGroup().first()
        assertEquals("", result.first)
        assertEquals(0L, result.second)
    }

    @Test
    fun `setLastActiveGroup with Unicode group name round-trips correctly`() = runTest {
        val name = "👨‍👩‍👧 My Family ❤️"
        prefs.setLastActiveGroup(name, 123456789L)
        val result = prefs.getLastActiveGroup().first()
        assertEquals(name, result.first)
        assertEquals(123456789L, result.second)
    }

    // ── Children serialization edge cases ──

    @Test
    fun `saveChildren with 100 children round-trips correctly`() = runTest {
        val children = (1..100).map { i ->
            ChildProfile(
                id = "child-$i",
                name = "Child $i",
                embedding = floatArrayOf(i.toFloat() * 0.01f, i.toFloat() * 0.02f),
                photoUri = if (i % 2 == 0) "file://photo$i.jpg" else null
            )
        }
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(100, result.size)
        for (i in 0 until 100) {
            assertEquals("child-${i + 1}", result[i].id)
            assertEquals("Child ${i + 1}", result[i].name)
            assertEquals((i + 1).toFloat() * 0.01f, result[i].embedding[0], 0.001f)
        }
    }

    @Test
    fun `saveChildren with emoji in child name round-trips correctly`() = runTest {
        val name = "👶 Baby Emma 🌟"
        val children = listOf(ChildProfile("id-emoji", name, floatArrayOf(0.5f), null))
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(1, result.size)
        assertEquals(name, result[0].name)
    }

    @Test
    fun `saveChildren with 512-dim embedding round-trips correctly`() = runTest {
        val embedding = FloatArray(512) { it * 0.001f - 0.256f }
        val children = listOf(ChildProfile("id-large", "BigEmbedding", embedding, null))
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(1, result.size)
        assertEquals(512, result[0].embedding.size)
        for (i in embedding.indices) {
            assertEquals("Mismatch at dim $i", embedding[i], result[0].embedding[i], 0.001f)
        }
    }

    @Test
    fun `saveChildren where all have null photoUri round-trips correctly`() = runTest {
        val children = listOf(
            ChildProfile("a", "Alice", floatArrayOf(0.1f), null),
            ChildProfile("b", "Bob", floatArrayOf(0.2f), null),
            ChildProfile("c", "Carol", floatArrayOf(0.3f), null)
        )
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(3, result.size)
        result.forEach { assertNull(it.photoUri) }
    }

    @Test
    fun `saveChildren with embedded path in photoUri round-trips correctly`() = runTest {
        val path = "/data/user/0/com.childfilter.app/files/children/abc-123.jpg"
        val children = listOf(ChildProfile("abc-123", "Alice", floatArrayOf(0.5f), path))
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(path, result[0].photoUri)
    }

    @Test
    fun `saveChildren with quotes in name escapes correctly`() = runTest {
        val name = """Alice "The" Smith"""
        val children = listOf(ChildProfile("id-q", name, floatArrayOf(0.1f, 0.2f), null))
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(1, result.size)
        assertEquals(name, result[0].name)
    }

    @Test
    fun `saveChildren with backslash in name escapes correctly`() = runTest {
        val name = "Child\\Name\\Test"
        val children = listOf(ChildProfile("id-bs", name, floatArrayOf(0.1f), null))
        prefs.saveChildren(children)
        val result = prefs.getChildren().first()
        assertEquals(name, result[0].name)
    }

    @Test
    fun `saveChildren replaces all existing children`() = runTest {
        prefs.saveChildren(listOf(
            ChildProfile("old-1", "Old Child", floatArrayOf(0.1f), null)
        ))
        prefs.saveChildren(listOf(
            ChildProfile("new-1", "New Child A", floatArrayOf(0.5f), null),
            ChildProfile("new-2", "New Child B", floatArrayOf(0.6f), null)
        ))
        val result = prefs.getChildren().first()
        assertEquals(2, result.size)
        assertEquals("New Child A", result[0].name)
        assertEquals("New Child B", result[1].name)
    }

    // ── Stats edge cases ──

    @Test
    fun `incrementProcessed 100 times gives count of 100`() = runTest {
        repeat(100) { prefs.incrementProcessed() }
        val stats = prefs.getStats().first()
        assertEquals(100, stats.first)
    }

    @Test
    fun `incrementMatched 50 times gives matched count of 50`() = runTest {
        repeat(50) { prefs.incrementMatched() }
        val stats = prefs.getStats().first()
        assertEquals(50, stats.second)
    }

    @Test
    fun `incrementMatched sets lastMatchTime to a recent timestamp`() = runTest {
        val before = System.currentTimeMillis()
        prefs.incrementMatched()
        val after = System.currentTimeMillis()
        val stats = prefs.getStats().first()
        assertTrue("lastMatchTime should be >= before", stats.third >= before)
        assertTrue("lastMatchTime should be <= after", stats.third <= after)
    }

    @Test
    fun `resetStats after large counts resets to zero`() = runTest {
        repeat(200) { prefs.incrementProcessed() }
        repeat(100) { prefs.incrementMatched() }
        prefs.resetStats()
        val stats = prefs.getStats().first()
        assertEquals(0, stats.first)
        assertEquals(0, stats.second)
        assertEquals(0L, stats.third)
    }

    @Test
    fun `incrementProcessed and incrementMatched are independent`() = runTest {
        prefs.incrementProcessed()
        prefs.incrementProcessed()
        prefs.incrementMatched()
        val stats = prefs.getStats().first()
        assertEquals(2, stats.first)
        assertEquals(1, stats.second)
    }

    // ── Activity log edge cases ──

    @Test
    fun `addLogEntry with JSON special characters in details stores correctly`() = runTest {
        val details = """{"key": "value", "nested": {"a": 1}}"""
        prefs.addLogEntry("match", details)
        val log = prefs.getActivityLog().first()
        assertEquals(1, log.size)
        assertEquals(details, log[0].details)
    }

    @Test
    fun `addLogEntry with very long details string stores correctly`() = runTest {
        val longDetails = "X".repeat(5000)
        prefs.addLogEntry("scan", longDetails)
        val log = prefs.getActivityLog().first()
        assertEquals(1, log.size)
        assertEquals(longDetails, log[0].details)
    }

    @Test
    fun `addLogEntry with quotes and backslashes in details stores correctly`() = runTest {
        val details = """File "photo.jpg" at path C:\Users\Photos"""
        prefs.addLogEntry("error", details)
        val log = prefs.getActivityLog().first()
        assertEquals(1, log.size)
        assertEquals(details, log[0].details)
    }

    @Test
    fun `activity log timestamp is set when entry is added`() = runTest {
        val before = System.currentTimeMillis()
        prefs.addLogEntry("scan", "Test entry")
        val after = System.currentTimeMillis()
        val log = prefs.getActivityLog().first()
        assertEquals(1, log.size)
        assertTrue("timestamp should be >= before", log[0].timestamp >= before)
        assertTrue("timestamp should be <= after", log[0].timestamp <= after)
    }

    @Test
    fun `addLogEntry exactly at 50 entries keeps all 50`() = runTest {
        for (i in 1..50) {
            prefs.addLogEntry("scan", "Entry $i")
        }
        val log = prefs.getActivityLog().first()
        assertEquals(50, log.size)
        assertEquals("Entry 50", log[0].details) // newest first
        assertEquals("Entry 1", log[49].details) // oldest last
    }

    @Test
    fun `addLogEntry at 51 drops oldest entry keeping 50`() = runTest {
        for (i in 1..51) {
            prefs.addLogEntry("scan", "Entry $i")
        }
        val log = prefs.getActivityLog().first()
        assertEquals(50, log.size)
        assertEquals("Entry 51", log[0].details) // newest first
        // Entry 1 is dropped
        assertFalse("Entry 1 should be dropped", log.any { it.details == "Entry 1" })
        assertTrue("Entry 2 should still be there", log.any { it.details == "Entry 2" })
    }

    @Test
    fun `activity log entries maintain newest-first order after many adds`() = runTest {
        for (i in 1..10) {
            prefs.addLogEntry("scan", "Entry $i")
        }
        val log = prefs.getActivityLog().first()
        // Newest (10) should be first, oldest (1) should be last
        assertEquals("Entry 10", log[0].details)
        assertEquals("Entry 9", log[1].details)
        assertEquals("Entry 1", log[9].details)
    }

    @Test
    fun `clearActivityLog then addLogEntry works correctly`() = runTest {
        prefs.addLogEntry("scan", "Entry A")
        prefs.clearActivityLog()
        prefs.addLogEntry("match", "Entry B")
        val log = prefs.getActivityLog().first()
        assertEquals(1, log.size)
        assertEquals("match", log[0].type)
        assertEquals("Entry B", log[0].details)
    }

    // ── Threshold edge cases ──

    @Test
    fun `saveThreshold negative value persists correctly`() = runTest {
        prefs.saveThreshold(-0.5f)
        val result = prefs.getThreshold().first()
        assertEquals(-0.5f, result, 0.001f)
    }

    @Test
    fun `saveThreshold greater than 1_0 persists correctly (no clamping)`() = runTest {
        prefs.saveThreshold(1.5f)
        val result = prefs.getThreshold().first()
        assertEquals(1.5f, result, 0.001f)
    }

    @Test
    fun `saveThreshold very small positive value persists with precision`() = runTest {
        prefs.saveThreshold(0.001f)
        val result = prefs.getThreshold().first()
        assertEquals(0.001f, result, 0.0001f)
    }

    @Test
    fun `saveThreshold overwrites previous value`() = runTest {
        prefs.saveThreshold(0.5f)
        prefs.saveThreshold(0.9f)
        prefs.saveThreshold(0.3f)
        val result = prefs.getThreshold().first()
        assertEquals(0.3f, result, 0.001f)
    }

    // ── Dark mode / notifications / tutorial — toggle back and forth ──

    @Test
    fun `setDarkMode toggles correctly multiple times`() = runTest {
        prefs.setDarkMode(true)
        assertTrue(prefs.getDarkMode().first())
        prefs.setDarkMode(false)
        assertFalse(prefs.getDarkMode().first())
        prefs.setDarkMode(true)
        assertTrue(prefs.getDarkMode().first())
    }

    @Test
    fun `setNotificationsEnabled toggles correctly multiple times`() = runTest {
        prefs.setNotificationsEnabled(false)
        assertFalse(prefs.getNotificationsEnabled().first())
        prefs.setNotificationsEnabled(true)
        assertTrue(prefs.getNotificationsEnabled().first())
    }

    @Test
    fun `setServiceEnabled toggling back and forth reflects correctly`() = runTest {
        repeat(5) { i ->
            val expected = i % 2 == 0
            prefs.setServiceEnabled(expected)
            assertEquals(expected, prefs.isServiceEnabled().first())
        }
    }

    // ── clearAllForTest isolation ──

    @Test
    fun `clearAllForTest wipes everything so defaults are returned`() = runTest {
        // Set everything
        prefs.saveEmbedding(floatArrayOf(0.1f, 0.2f))
        prefs.saveThreshold(0.9f)
        prefs.setServiceEnabled(true)
        prefs.addKnownGroup("Group X")
        prefs.saveSelectedGroups(setOf("Group X"))
        prefs.setLastActiveGroup("Group X", 999L)
        prefs.saveChildren(listOf(ChildProfile("id", "Alice", floatArrayOf(0.1f), null)))
        prefs.setOnboardingCompleted(true)
        prefs.incrementProcessed()
        prefs.incrementMatched()
        prefs.addLogEntry("scan", "entry")
        prefs.setDarkMode(true)
        prefs.setNotificationsEnabled(false)
        prefs.setHasSeenTutorial(true)

        // Wipe
        prefs.clearAllForTest()

        // Verify defaults
        assertNull(prefs.getEmbedding().first())
        assertEquals(0.75f, prefs.getThreshold().first(), 0.001f)
        assertFalse(prefs.isServiceEnabled().first())
        assertTrue(prefs.getKnownGroups().first().isEmpty())
        assertTrue(prefs.getSelectedGroups().first().isEmpty())
        assertEquals("", prefs.getLastActiveGroup().first().first)
        assertEquals(0L, prefs.getLastActiveGroup().first().second)
        assertTrue(prefs.getChildren().first().isEmpty())
        assertFalse(prefs.hasCompletedOnboarding().first())
        assertEquals(0, prefs.getStats().first().first)
        assertEquals(0, prefs.getStats().first().second)
        assertEquals(0L, prefs.getStats().first().third)
        assertTrue(prefs.getActivityLog().first().isEmpty())
        assertFalse(prefs.getDarkMode().first())
        assertTrue(prefs.getNotificationsEnabled().first())
        assertFalse(prefs.hasSeenTutorial().first())
    }
}
