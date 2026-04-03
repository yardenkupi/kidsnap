package com.childfilter.app.unit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.childfilter.app.data.AppPreferences
import com.childfilter.app.data.ChildProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Deep tests for the custom JSON serialization / deserialization in AppPreferences.
 *
 * AppPreferences hand-rolls its own JSON encoder for ChildProfile (no external library),
 * using escapeJson() + findUnescapedQuote() + extractJsonString().
 * These tests verify correctness of that custom serializer under adversarial inputs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ChildProfileSerializationTest {

    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = AppPreferences(context)
        runBlocking { prefs.clearAllForTest() }
    }

    private fun profile(
        id: String,
        name: String,
        embedding: FloatArray = floatArrayOf(0.5f),
        photoUri: String? = null
    ) = ChildProfile(id, name, embedding, photoUri)

    private suspend fun roundTrip(children: List<ChildProfile>): List<ChildProfile> {
        prefs.saveChildren(children)
        return prefs.getChildren().first()
    }

    // ── Name escaping ──

    @Test
    fun `name with double-quote survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", """Alice "the" Smith""")))
        assertEquals("""Alice "the" Smith""", result[0].name)
    }

    @Test
    fun `name with backslash survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", """C:\Users\Alice""")))
        assertEquals("""C:\Users\Alice""", result[0].name)
    }

    @Test
    fun `name with backslash followed by double-quote survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", """path\"escaped""")))
        assertEquals("""path\"escaped""", result[0].name)
    }

    @Test
    fun `name with multiple consecutive quotes survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", """say ""hello"" world""")))
        assertEquals("""say ""hello"" world""", result[0].name)
    }

    @Test
    fun `name with single quote (apostrophe) survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", "O'Brien")))
        assertEquals("O'Brien", result[0].name)
    }

    @Test
    fun `name with curly braces survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", "Name{With}Braces")))
        assertEquals("Name{With}Braces", result[0].name)
    }

    @Test
    fun `name with square brackets survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", "Name[With]Brackets")))
        assertEquals("Name[With]Brackets", result[0].name)
    }

    @Test
    fun `name with comma (could confuse naive parsers) survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", "Smith, John")))
        assertEquals("Smith, John", result[0].name)
    }

    @Test
    fun `name with colon survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", "Group: Alice")))
        assertEquals("Group: Alice", result[0].name)
    }

    @Test
    fun `name with newline character survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", "Alice\nSmith")))
        assertEquals("Alice\nSmith", result[0].name)
    }

    @Test
    fun `name with tab character survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", "Alice\tSmith")))
        assertEquals("Alice\tSmith", result[0].name)
    }

    @Test
    fun `name with null character survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", "Alice\u0000Smith")))
        assertEquals("Alice\u0000Smith", result[0].name)
    }

    @Test
    fun `name with Unicode emoji survives round-trip`() = runTest {
        val name = "👶 Baby Emma 🌟🎉"
        val result = roundTrip(listOf(profile("id1", name)))
        assertEquals(name, result[0].name)
    }

    @Test
    fun `name with Arabic text survives round-trip`() = runTest {
        val name = "عائلة الأطفال الصغار"
        val result = roundTrip(listOf(profile("id1", name)))
        assertEquals(name, result[0].name)
    }

    @Test
    fun `name with Chinese characters survives round-trip`() = runTest {
        val name = "小朋友 Alice"
        val result = roundTrip(listOf(profile("id1", name)))
        assertEquals(name, result[0].name)
    }

    // ── ID escaping ──

    @Test
    fun `id with hyphens and digits survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("abc-def-123-456", "Alice")))
        assertEquals("abc-def-123-456", result[0].id)
    }

    @Test
    fun `id with UUID format survives round-trip`() = runTest {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        val result = roundTrip(listOf(profile(uuid, "Alice")))
        assertEquals(uuid, result[0].id)
    }

    // ── Embedding edge cases ──

    @Test
    fun `embedding with negative values survives round-trip`() = runTest {
        val embedding = floatArrayOf(-0.5f, -0.3f, -0.8f, 0.1f)
        val result = roundTrip(listOf(profile("id1", "Alice", embedding)))
        assertEquals(4, result[0].embedding.size)
        for (i in embedding.indices) {
            assertEquals("dim $i", embedding[i], result[0].embedding[i], 0.0001f)
        }
    }

    @Test
    fun `embedding with zero values survives round-trip`() = runTest {
        val embedding = FloatArray(128) { 0f }
        val result = roundTrip(listOf(profile("id1", "Alice", embedding)))
        assertEquals(128, result[0].embedding.size)
        for (v in result[0].embedding) assertEquals(0f, v, 0.0001f)
    }

    @Test
    fun `embedding with very small values survives round-trip`() = runTest {
        val embedding = floatArrayOf(1.23456789E-7f, -9.87654321E-8f, 0f)
        val result = roundTrip(listOf(profile("id1", "Alice", embedding)))
        for (i in embedding.indices) {
            assertEquals("dim $i", embedding[i], result[0].embedding[i], 1e-10f + kotlin.math.abs(embedding[i]) * 0.01f)
        }
    }

    // ── PhotoUri edge cases ──

    @Test
    fun `null photoUri survives round-trip`() = runTest {
        val result = roundTrip(listOf(profile("id1", "Alice", floatArrayOf(0.1f), null)))
        assertNull(result[0].photoUri)
    }

    @Test
    fun `absolute file path photoUri survives round-trip`() = runTest {
        val path = "/data/user/0/com.childfilter.app/files/children/child-123.jpg"
        val result = roundTrip(listOf(profile("id1", "Alice", floatArrayOf(0.1f), path)))
        assertEquals(path, result[0].photoUri)
    }

    @Test
    fun `photoUri with spaces survives round-trip`() = runTest {
        val uri = "/files/my photo album/child.jpg"
        val result = roundTrip(listOf(profile("id1", "Alice", floatArrayOf(0.1f), uri)))
        assertEquals(uri, result[0].photoUri)
    }

    // ── Multi-child serialization ──

    @Test
    fun `mixed null and non-null photoUri preserves correctly`() = runTest {
        val children = listOf(
            profile("id1", "Alice", floatArrayOf(0.1f), "/path/a.jpg"),
            profile("id2", "Bob", floatArrayOf(0.2f), null),
            profile("id3", "Carol", floatArrayOf(0.3f), "/path/c.jpg"),
            profile("id4", "Dave", floatArrayOf(0.4f), null)
        )
        val result = roundTrip(children)
        assertEquals(4, result.size)
        assertEquals("/path/a.jpg", result[0].photoUri)
        assertNull(result[1].photoUri)
        assertEquals("/path/c.jpg", result[2].photoUri)
        assertNull(result[3].photoUri)
    }

    @Test
    fun `50 children with all edge-case names survive round-trip`() = runTest {
        val specialChars = listOf(
            """Alice "Q" Smith""", "O'Brien", "Test\\Name", "Smith, Jr.", "Alice\nSmith",
            "Tab\tName", "Colon: Name", "Braces{Name}", "[Square]", "Emoji 👶"
        )
        val children = (0 until 50).map { i ->
            val name = "${specialChars[i % specialChars.size]} $i"
            profile("id-$i", name, FloatArray(4) { (i + it).toFloat() * 0.1f })
        }
        val result = roundTrip(children)
        assertEquals(50, result.size)
        for (i in 0 until 50) {
            val expectedName = "${specialChars[i % specialChars.size]} $i"
            assertEquals("Child $i name mismatch", expectedName, result[i].name)
            assertEquals("id-$i", result[i].id)
        }
    }

    @Test
    fun `saving empty list after having children gives empty list`() = runTest {
        val children = (1..5).map { profile("id-$it", "Child $it") }
        prefs.saveChildren(children)
        assertEquals(5, prefs.getChildren().first().size)

        prefs.saveChildren(emptyList())
        assertTrue(prefs.getChildren().first().isEmpty())
    }

    @Test
    fun `clearChildren gives empty list`() = runTest {
        prefs.saveChildren(listOf(profile("id1", "Alice")))
        prefs.clearChildren()
        assertTrue(prefs.getChildren().first().isEmpty())
    }

    @Test
    fun `saving after clear adds new children correctly`() = runTest {
        prefs.saveChildren(listOf(profile("old-1", "Old Child")))
        prefs.clearChildren()
        prefs.saveChildren(listOf(
            profile("new-1", "New Child A"),
            profile("new-2", "New Child B")
        ))
        val result = prefs.getChildren().first()
        assertEquals(2, result.size)
        assertEquals("New Child A", result[0].name)
        assertEquals("New Child B", result[1].name)
    }
}
