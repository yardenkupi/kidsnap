package com.childfilter.app.unit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.childfilter.app.ml.FaceNetHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class FaceNetHelperEdgeCaseTest {

    private fun makeHelper(): FaceNetHelper {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return FaceNetHelper(context)
    }

    private fun makeBitmap(w: Int, h: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap =
        Bitmap.createBitmap(w, h, config)

    private fun makeSolidBitmap(w: Int, h: Int, color: Int): Bitmap {
        val bmp = makeBitmap(w, h)
        android.graphics.Canvas(bmp).drawColor(color)
        return bmp
    }

    // ── Model loading state ──

    @Test
    fun `isModelLoaded is false in Robolectric (no real assets)`() {
        assertFalse(makeHelper().isModelLoaded)
    }

    @Test
    fun `multiple FaceNetHelper instances all have isModelLoaded false`() {
        val helpers = (1..5).map { makeHelper() }
        helpers.forEach { assertFalse(it.isModelLoaded) }
        helpers.forEach { it.close() }
    }

    // ── getEmbedding throws for various bitmap sizes (model not loaded) ──

    @Test
    fun `getEmbedding on 1x1 bitmap throws IllegalStateException`() {
        expectIllegalState { makeHelper().getEmbedding(makeBitmap(1, 1)) }
    }

    @Test
    fun `getEmbedding on 4x4 bitmap throws IllegalStateException`() {
        expectIllegalState { makeHelper().getEmbedding(makeBitmap(4, 4)) }
    }

    @Test
    fun `getEmbedding on 112x112 bitmap throws IllegalStateException`() {
        expectIllegalState { makeHelper().getEmbedding(makeBitmap(112, 112)) }
    }

    @Test
    fun `getEmbedding on 160x160 bitmap (model input size) throws IllegalStateException`() {
        expectIllegalState { makeHelper().getEmbedding(makeBitmap(160, 160)) }
    }

    @Test
    fun `getEmbedding on 320x320 oversized bitmap throws IllegalStateException`() {
        expectIllegalState { makeHelper().getEmbedding(makeBitmap(320, 320)) }
    }

    @Test
    fun `getEmbedding on 1000x1000 very large bitmap throws IllegalStateException`() {
        expectIllegalState { makeHelper().getEmbedding(makeBitmap(1000, 1000)) }
    }

    @Test
    fun `getEmbedding on RGB_565 bitmap throws IllegalStateException`() {
        expectIllegalState { makeHelper().getEmbedding(makeBitmap(112, 112, Bitmap.Config.RGB_565)) }
    }

    @Test
    fun `getEmbedding on all-black 160x160 bitmap throws IllegalStateException`() {
        val black = makeSolidBitmap(160, 160, Color.BLACK)
        expectIllegalState { makeHelper().getEmbedding(black) }
    }

    @Test
    fun `getEmbedding on all-white 160x160 bitmap throws IllegalStateException`() {
        val white = makeSolidBitmap(160, 160, Color.WHITE)
        expectIllegalState { makeHelper().getEmbedding(white) }
    }

    @Test
    fun `getEmbedding on all-red 160x160 bitmap throws IllegalStateException`() {
        val red = makeSolidBitmap(160, 160, Color.RED)
        expectIllegalState { makeHelper().getEmbedding(red) }
    }

    @Test
    fun `getEmbedding on non-square 200x100 bitmap throws IllegalStateException`() {
        expectIllegalState { makeHelper().getEmbedding(makeBitmap(200, 100)) }
    }

    // ── isSamePerson edge cases ──

    @Test
    fun `isSamePerson with identical zero vectors returns false (no similarity)`() {
        val helper = makeHelper()
        val zero = floatArrayOf(0f, 0f, 0f, 0f)
        // Zero vectors have no direction — cosine similarity = 0, which is below 0.75
        assertFalse(helper.isSamePerson(zero, zero, 0.75f))
    }

    @Test
    fun `isSamePerson with threshold 0_0f returns true for zero vectors`() {
        val helper = makeHelper()
        val zero = floatArrayOf(0f, 0f, 0f)
        // cosine(zero, zero) = 0, and 0 >= 0.0 is true
        assertTrue(helper.isSamePerson(zero, zero, 0.0f))
    }

    @Test
    fun `isSamePerson with all-negative same-direction embeddings returns true`() {
        val helper = makeHelper()
        val a = floatArrayOf(-0.6f, -0.8f, 0f) // normalized, pointing in negative direction
        val b = floatArrayOf(-0.3f, -0.4f, 0f) // same direction, different magnitude
        // cosine similarity = 1.0 (same direction)
        assertTrue(helper.isSamePerson(a, b, 0.99f))
    }

    @Test
    fun `isSamePerson with embedding scaled by negative gives -1_0 similarity`() {
        val helper = makeHelper()
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-5f, 0f, 0f) // opposite direction
        assertFalse(helper.isSamePerson(a, b, 0.0f)) // 0.0 < -1.0? No: -1.0 >= 0.0 is FALSE
        // Actually similarity=-1.0, which is NOT >= 0.0
        assertFalse(helper.isSamePerson(a, b, 0.0f))
    }

    @Test
    fun `isSamePerson with 128-dim unit vectors at exact threshold`() {
        val helper = makeHelper()
        // Create two vectors with cosine similarity = 0.75
        val cosTheta = 0.75f
        val sinTheta = sqrt(1f - cosTheta * cosTheta)
        val a = floatArrayOf(1f, 0f) // 2-dim for simplicity
        val b = floatArrayOf(cosTheta, sinTheta)
        // At exactly the threshold, should match (>= 0.75)
        assertTrue(helper.isSamePerson(a, b, 0.75f))
        // Just above threshold, should not match
        assertFalse(helper.isSamePerson(a, b, 0.76f))
    }

    @Test
    fun `isSamePerson with empty float arrays behaves gracefully`() {
        val helper = makeHelper()
        // Empty arrays: dot=0, norms=0 → cosine=0 → not same person at 0.75
        val a = floatArrayOf()
        val b = floatArrayOf()
        assertFalse(helper.isSamePerson(a, b, 0.75f))
    }

    // ── similarityScore edge cases ──

    @Test
    fun `similarityScore of 128-dim all-ones with all-ones is 1_0`() {
        val helper = makeHelper()
        val v = FloatArray(128) { 1f }
        assertEquals(1.0f, helper.similarityScore(v, v), 0.0001f)
    }

    @Test
    fun `similarityScore of 128-dim all-ones with all-negatives is -1_0`() {
        val helper = makeHelper()
        val pos = FloatArray(128) { 1f }
        val neg = FloatArray(128) { -1f }
        assertEquals(-1.0f, helper.similarityScore(pos, neg), 0.0001f)
    }

    @Test
    fun `similarityScore with very small magnitude vectors is in range -1 to 1`() {
        val helper = makeHelper()
        val tiny = FloatArray(128) { 1e-10f }
        val score = helper.similarityScore(tiny, tiny)
        assertTrue("Tiny vector self-similarity should be close to 1.0", score >= 0.9f)
    }

    @Test
    fun `similarityScore always returns finite value for non-zero vectors`() {
        val helper = makeHelper()
        val testPairs = listOf(
            FloatArray(128) { 1f } to FloatArray(128) { 1f },
            FloatArray(128) { -1f } to FloatArray(128) { 1f },
            FloatArray(128) { it.toFloat() } to FloatArray(128) { (128 - it).toFloat() },
            FloatArray(512) { kotlin.math.sin(it.toDouble()).toFloat() } to
                FloatArray(512) { kotlin.math.cos(it.toDouble()).toFloat() }
        )
        for ((a, b) in testPairs) {
            if (a.size == b.size) {
                val score = helper.similarityScore(a, b)
                assertTrue("Score should be finite", score.isFinite())
                assertTrue("Score should be in [-1,1]", score >= -1.0001f && score <= 1.0001f)
            }
        }
    }

    @Test
    fun `similarityScore of nearly-identical embeddings is close to 1`() {
        val helper = makeHelper()
        val base = FloatArray(128) { (it + 1).toFloat() * 0.01f }
        val nearlyIdentical = FloatArray(128) { base[it] + 0.0001f } // tiny perturbation
        val score = helper.similarityScore(base, nearlyIdentical)
        assertTrue("Nearly identical vectors should have similarity > 0.999", score > 0.999f)
    }

    @Test
    fun `similarityScore is reflexive - score with itself is 1_0`() {
        val helper = makeHelper()
        val vectors = listOf(
            floatArrayOf(0.3f, 0.4f, 0.5f),
            floatArrayOf(-1f, -1f, -1f),
            FloatArray(128) { it.toFloat() },
            floatArrayOf(0.001f, 0.999f)
        )
        for (v in vectors) {
            assertEquals("Similarity with self should be 1.0", 1.0f, helper.similarityScore(v, v), 0.001f)
        }
    }

    @Test
    fun `similarityScore is symmetric for various vectors`() {
        val helper = makeHelper()
        val pairs = listOf(
            floatArrayOf(1f, 2f, 3f) to floatArrayOf(4f, 5f, 6f),
            floatArrayOf(-1f, 0f, 1f) to floatArrayOf(0.5f, -0.5f, 0.5f),
            FloatArray(128) { it.toFloat() } to FloatArray(128) { (128 - it).toFloat() }
        )
        for ((a, b) in pairs) {
            assertEquals(
                "sim(a,b) should equal sim(b,a)",
                helper.similarityScore(a, b),
                helper.similarityScore(b, a),
                0.0001f
            )
        }
    }

    // ── close() is idempotent ──

    @Test
    fun `close can be called multiple times without crashing`() {
        val helper = makeHelper()
        helper.close()
        helper.close() // second close should not throw
    }

    @Test
    fun `isSamePerson still works after close (no crash)`() {
        val helper = makeHelper()
        helper.close()
        val a = floatArrayOf(0.6f, 0.8f)
        val b = floatArrayOf(0.6f, 0.8f)
        // After close, model is not loaded — isSamePerson uses similarityScore which is pure math
        // Should not throw
        val result = helper.isSamePerson(a, b, 0.75f)
        assertTrue("Identical embeddings should match even after close", result)
    }

    // ── Helper ──

    private fun expectIllegalState(block: () -> Unit) {
        try {
            block()
            assertTrue("Expected IllegalStateException to be thrown", false)
        } catch (e: IllegalStateException) {
            // Expected
        }
    }
}
