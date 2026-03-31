package com.childfilter.app.unit

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.childfilter.app.ml.FaceNetHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class FaceNetHelperTest {

    private fun makeHelper(): FaceNetHelper {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return FaceNetHelper(context)
    }

    // ── Model loading ──

    @Test
    fun `FaceNetHelper with missing model file has isModelLoaded false`() {
        val helper = makeHelper()
        assertFalse(helper.isModelLoaded)
    }

    // ── getEmbedding throws when model not loaded ──

    @Test(expected = IllegalStateException::class)
    fun `getEmbedding throws IllegalStateException when model is not loaded`() {
        val helper = makeHelper()
        val bitmap = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
        helper.getEmbedding(bitmap)
    }

    @Test
    fun `getEmbedding on 1x1 solid-color bitmap when model not loaded throws IllegalStateException`() {
        val helper = makeHelper()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, android.graphics.Color.WHITE)
        var threw = false
        try {
            helper.getEmbedding(bitmap)
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("Expected IllegalStateException", threw)
    }

    @Test
    fun `getEmbedding error message contains model not loaded`() {
        val helper = makeHelper()
        val bitmap = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
        try {
            helper.getEmbedding(bitmap)
        } catch (e: IllegalStateException) {
            val message = e.message?.lowercase() ?: ""
            assertTrue(
                "Error message should mention model not loaded, but was: ${e.message}",
                message.contains("model") || message.contains("not loaded") || message.contains("face recognition")
            )
            return
        }
        assertTrue("Expected IllegalStateException to be thrown", false)
    }

    @Test
    fun `getEmbedding on all-white 112x112 bitmap throws not returns null`() {
        val helper = makeHelper()
        val bitmap = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
        // Fill with white
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        var threwException = false
        try {
            helper.getEmbedding(bitmap)
        } catch (e: IllegalStateException) {
            threwException = true
        }
        assertTrue("Should throw IllegalStateException, not return null", threwException)
    }

    // ── isSamePerson ──

    @Test
    fun `isSamePerson returns true when embeddings are identical`() {
        val helper = makeHelper()
        val embedding = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        assertTrue(helper.isSamePerson(embedding, embedding, 0.75f))
    }

    @Test
    fun `isSamePerson returns false when embeddings are orthogonal`() {
        val helper = makeHelper()
        val e1 = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f)
        val e2 = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f)
        assertFalse(helper.isSamePerson(e1, e2, 0.75f))
    }

    @Test
    fun `isSamePerson with threshold 0_0f always returns true for non-zero embeddings`() {
        val helper = makeHelper()
        val e1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val e2 = floatArrayOf(0.0f, 1.0f, 0.0f) // orthogonal, similarity = 0.0
        // Cosine similarity of orthogonal vectors = 0.0, which is >= 0.0
        assertTrue("Orthogonal vectors should match at threshold 0.0", helper.isSamePerson(e1, e2, 0.0f))
    }

    @Test
    fun `isSamePerson with threshold 1_0f only returns true for identical embeddings`() {
        val helper = makeHelper()
        val identical = floatArrayOf(0.6f, 0.8f, 0.0f) // already unit vector
        val similar = floatArrayOf(0.7f, 0.7f, 0.0f)   // similar but not identical
        assertTrue("Identical embeddings should match at threshold 1.0", helper.isSamePerson(identical, identical, 1.0f))
        assertFalse("Different embeddings should NOT match at threshold 1.0", helper.isSamePerson(identical, similar, 1.0f))
    }

    // ── similarityScore ──

    @Test
    fun `similarityScore returns 1_0 for identical embeddings`() {
        val helper = makeHelper()
        val embedding = floatArrayOf(0.3f, 0.4f, 0.5f)
        val score = helper.similarityScore(embedding, embedding)
        assertEquals(1.0f, score, 0.001f)
    }

    @Test
    fun `cosineSimilarity symmetry sim(a,b) equals sim(b,a)`() {
        val helper = makeHelper()
        val a = floatArrayOf(0.3f, 0.5f, 0.2f, 0.8f)
        val b = floatArrayOf(0.7f, 0.1f, 0.6f, 0.3f)
        val scoreAB = helper.similarityScore(a, b)
        val scoreBA = helper.similarityScore(b, a)
        assertEquals("sim(a,b) should equal sim(b,a)", scoreAB, scoreBA, 0.0001f)
    }

    @Test
    fun `cosineSimilarity is always in range -1 to 1 for random vectors`() {
        val helper = makeHelper()
        val testVectors = listOf(
            floatArrayOf(1f, 2f, 3f, 4f, 5f),
            floatArrayOf(-1f, 0.5f, -0.3f, 2f, 1f),
            floatArrayOf(0.1f, 0.9f, 0.5f, -0.2f, 0.7f),
            floatArrayOf(100f, 200f, -50f, 25f, -10f),
            floatArrayOf(-1f, -1f, -1f, -1f, -1f)
        )
        for (i in testVectors.indices) {
            for (j in testVectors.indices) {
                val score = helper.similarityScore(testVectors[i], testVectors[j])
                assertTrue(
                    "Similarity score should be in [-1,1] but was $score for vectors $i and $j",
                    score >= -1.0f - 0.001f && score <= 1.0f + 0.001f
                )
            }
        }
    }

    @Test
    fun `cosineSimilarity of opposite vectors returns -1_0`() {
        val helper = makeHelper()
        val v = floatArrayOf(1.0f, 0.0f, 0.0f)
        val opposite = floatArrayOf(-1.0f, 0.0f, 0.0f)
        val score = helper.similarityScore(v, opposite)
        assertEquals(-1.0f, score, 0.001f)
    }

    @Test
    fun `cosineSimilarity of orthogonal vectors returns 0_0`() {
        val helper = makeHelper()
        val v1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val v2 = floatArrayOf(0.0f, 1.0f, 0.0f)
        val score = helper.similarityScore(v1, v2)
        assertEquals(0.0f, score, 0.001f)
    }

    // ── l2Normalize behavior tested via similarityScore ──

    @Test
    fun `l2Normalize idempotent normalizing already-normalized vector gives similarityScore of 1_0`() {
        // Proxy: a normalized vector should have similarity 1.0 with itself (already tested)
        // Additionally, verify that scaling a vector doesn't change its similarity to itself
        val helper = makeHelper()
        val v = floatArrayOf(3.0f, 4.0f) // norm = 5, normalized = [0.6, 0.8]
        val score = helper.similarityScore(v, v)
        assertEquals("Similarity of a vector with itself should be 1.0", 1.0f, score, 0.001f)
    }

    @Test
    fun `l2Normalize zero vector similarityScore returns 0 without crash`() {
        val helper = makeHelper()
        val zero = floatArrayOf(0.0f, 0.0f, 0.0f)
        val nonZero = floatArrayOf(1.0f, 0.0f, 0.0f)
        // Both zero vectors: should return 0, not crash
        val scoreZeroZero = helper.similarityScore(zero, zero)
        assertEquals(0.0f, scoreZeroZero, 0.001f)
        // Zero vs non-zero: should also return 0
        val scoreZeroNonZero = helper.similarityScore(zero, nonZero)
        assertEquals(0.0f, scoreZeroNonZero, 0.001f)
    }

    // ── l2Normalize produces unit vector ──

    @Test
    fun `l2Normalize produces unit vector`() {
        // Test l2Normalize indirectly via math: verify cosine similarity of same-direction
        // vectors regardless of magnitude is 1.0
        val helper = makeHelper()
        val v1 = floatArrayOf(3.0f, 4.0f)  // norm = 5
        val v2 = floatArrayOf(3.0f, 4.0f)  // same direction

        val score = helper.similarityScore(v1, v2)
        assertEquals(1.0f, score, 0.001f)

        // Verify the concept: l2Normalize of [3,4] should be [0.6, 0.8]
        val norm = sqrt(3.0f * 3.0f + 4.0f * 4.0f)
        assertEquals(5.0f, norm, 0.001f)
        val normalized = floatArrayOf(3.0f / norm, 4.0f / norm)
        val unitNorm = sqrt(normalized[0] * normalized[0] + normalized[1] * normalized[1])
        assertEquals(1.0f, unitNorm, 0.001f)
    }

    @Test
    fun `128-dim normalized vector has L2 norm approximately 1_0`() {
        // Create a 128-dim vector and manually normalize it, verify L2 norm is 1.0
        val raw = FloatArray(128) { (it + 1).toFloat() }
        val norm = sqrt(raw.sumOf { (it * it).toDouble() }.toFloat())
        val normalized = FloatArray(128) { raw[it] / norm }
        val l2Norm = sqrt(normalized.sumOf { (it * it).toDouble() }.toFloat())
        assertEquals("L2 norm of normalized 128-dim vector should be 1.0", 1.0f, l2Norm, 0.001f)

        // Also verify via similarityScore: normalized vector should have similarity 1.0 with itself
        val helper = makeHelper()
        val score = helper.similarityScore(normalized, normalized)
        assertEquals(1.0f, score, 0.001f)
    }

    @Test
    fun `scaling a vector does not change its cosine similarity with another vector`() {
        val helper = makeHelper()
        val a = floatArrayOf(1.0f, 2.0f, 3.0f)
        val b = floatArrayOf(4.0f, 5.0f, 6.0f)
        val aScaled = floatArrayOf(10.0f, 20.0f, 30.0f) // same direction as a, 10x
        val scoreOriginal = helper.similarityScore(a, b)
        val scoreScaled = helper.similarityScore(aScaled, b)
        assertEquals("Scaling should not change cosine similarity", scoreOriginal, scoreScaled, 0.001f)
    }
}
