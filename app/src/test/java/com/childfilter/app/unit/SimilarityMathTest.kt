package com.childfilter.app.unit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure math unit tests for cosine similarity, L2 normalization, and dot product properties.
 * No Android or Robolectric needed — these are plain JUnit4 tests.
 *
 * The formulas mirror what FaceNetHelper uses internally.
 */
class SimilarityMathTest {

    // ── Math helpers (mirroring FaceNetHelper internals) ──

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    private fun l2Norm(v: FloatArray): Float = sqrt(v.sumOf { (it * it).toDouble() }.toFloat())

    private fun l2Normalize(v: FloatArray): FloatArray {
        val norm = l2Norm(v)
        return if (norm == 0f) v else FloatArray(v.size) { v[it] / norm }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val d = sqrt(na) * sqrt(nb)
        return if (d == 0f) 0f else dot / d
    }

    // ── Cosine similarity tests ──

    @Test
    fun `cosine similarity of identical unit vectors equals 1_0`() {
        val v = floatArrayOf(0.6f, 0.8f, 0.0f) // already normalized: sqrt(0.36+0.64) = 1.0
        val result = cosineSimilarity(v, v)
        assertEquals(1.0f, result, 0.0001f)
    }

    @Test
    fun `cosine similarity of orthogonal vectors equals 0_0`() {
        val v1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val v2 = floatArrayOf(0.0f, 1.0f, 0.0f)
        val result = cosineSimilarity(v1, v2)
        assertEquals(0.0f, result, 0.0001f)
    }

    @Test
    fun `cosine similarity of opposite unit vectors equals -1_0`() {
        val v1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val v2 = floatArrayOf(-1.0f, 0.0f, 0.0f)
        val result = cosineSimilarity(v1, v2)
        assertEquals(-1.0f, result, 0.0001f)
    }

    @Test
    fun `cosine similarity is symmetric`() {
        val a = floatArrayOf(0.3f, 0.5f, 0.2f, 0.8f)
        val b = floatArrayOf(0.7f, 0.1f, 0.6f, 0.3f)
        val ab = cosineSimilarity(a, b)
        val ba = cosineSimilarity(b, a)
        assertEquals(ab, ba, 0.0001f)
    }

    @Test
    fun `cosine similarity is always in range -1 to 1`() {
        val testPairs = listOf(
            floatArrayOf(1f, 2f, 3f) to floatArrayOf(4f, 5f, 6f),
            floatArrayOf(-1f, 0.5f, -0.3f) to floatArrayOf(2f, 1f, 0.5f),
            floatArrayOf(100f, 200f, -50f) to floatArrayOf(-100f, -200f, 50f),
            floatArrayOf(0.1f, 0.1f, 0.1f) to floatArrayOf(-0.1f, -0.1f, -0.1f),
            floatArrayOf(1f, 0f, 0f) to floatArrayOf(0f, 0f, 1f),
            floatArrayOf(0.5f, 0.5f, 0.5f) to floatArrayOf(-0.5f, 0.5f, -0.5f)
        )
        for ((a, b) in testPairs) {
            val score = cosineSimilarity(a, b)
            assertTrue(
                "Cosine similarity should be in [-1,1] but was $score",
                score >= -1.0f - 0.0001f && score <= 1.0f + 0.0001f
            )
        }
    }

    @Test
    fun `cosine similarity of zero vectors returns 0 without crash`() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val v = floatArrayOf(1f, 0f, 0f)
        assertEquals(0.0f, cosineSimilarity(zero, zero), 0.0001f)
        assertEquals(0.0f, cosineSimilarity(zero, v), 0.0001f)
        assertEquals(0.0f, cosineSimilarity(v, zero), 0.0001f)
    }

    // ── L2 norm and normalization tests ──

    @Test
    fun `L2 norm of vector 3 4 equals 5_0`() {
        val v = floatArrayOf(3f, 4f)
        val norm = l2Norm(v)
        assertEquals(5.0f, norm, 0.0001f)
    }

    @Test
    fun `L2 norm of unit vector 1 0 0 equals 1_0`() {
        val v = floatArrayOf(1f, 0f, 0f)
        assertEquals(1.0f, l2Norm(v), 0.0001f)
    }

    @Test
    fun `after normalization vector 3 4 becomes 0_6 0_8`() {
        val v = floatArrayOf(3f, 4f)
        val normalized = l2Normalize(v)
        assertEquals(2, normalized.size)
        assertEquals(0.6f, normalized[0], 0.0001f)
        assertEquals(0.8f, normalized[1], 0.0001f)
    }

    @Test
    fun `normalized vector has L2 norm of 1_0`() {
        val vectors = listOf(
            floatArrayOf(3f, 4f),
            floatArrayOf(1f, 2f, 3f),
            floatArrayOf(-1f, -1f, -1f, -1f),
            FloatArray(128) { (it + 1).toFloat() }
        )
        for (v in vectors) {
            val normalized = l2Normalize(v)
            val norm = l2Norm(normalized)
            assertEquals("Normalized vector should have L2 norm of 1.0", 1.0f, norm, 0.0001f)
        }
    }

    @Test
    fun `normalizing an already-normalized vector is idempotent`() {
        val v = floatArrayOf(0.6f, 0.8f) // already normalized
        val doubleNormalized = l2Normalize(l2Normalize(v))
        assertEquals(v[0], doubleNormalized[0], 0.0001f)
        assertEquals(v[1], doubleNormalized[1], 0.0001f)
    }

    @Test
    fun `normalizing zero vector returns zero vector without crash`() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val result = l2Normalize(zero)
        assertEquals(3, result.size)
        assertEquals(0f, result[0], 0.0001f)
        assertEquals(0f, result[1], 0.0001f)
        assertEquals(0f, result[2], 0.0001f)
    }

    // ── Dot product property tests ──

    @Test
    fun `dot product is commutative`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(4f, 5f, 6f)
        assertEquals(dotProduct(a, b), dotProduct(b, a), 0.0001f)
    }

    @Test
    fun `dot product is distributive over addition`() {
        // a · (b + c) = a·b + a·c
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(4f, 5f, 6f)
        val c = floatArrayOf(7f, 8f, 9f)
        val bPlusC = FloatArray(3) { b[it] + c[it] }
        val lhs = dotProduct(a, bPlusC)
        val rhs = dotProduct(a, b) + dotProduct(a, c)
        assertEquals(lhs, rhs, 0.0001f)
    }

    @Test
    fun `dot product of vector with itself equals squared L2 norm`() {
        val v = floatArrayOf(2f, 3f, 6f) // norm = sqrt(4+9+36) = 7
        val dotSelf = dotProduct(v, v)
        val normSquared = l2Norm(v) * l2Norm(v)
        assertEquals(normSquared, dotSelf, 0.001f)
        assertEquals(49.0f, dotSelf, 0.001f)
    }

    // ── Additional cosine similarity with manual verification ──

    @Test
    fun `cosine similarity matches manual calculation`() {
        // a = [1, 0], b = [1, 1] — manually: dot=1, |a|=1, |b|=sqrt(2), cos=1/sqrt(2)≈0.7071
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(1f, 1f)
        val expected = 1f / sqrt(2f)
        val result = cosineSimilarity(a, b)
        assertEquals(expected, result, 0.0001f)
    }

    @Test
    fun `cosine similarity of scaled vectors is same as unscaled`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(4f, 5f, 6f)
        val aScaled = FloatArray(3) { a[it] * 100f }
        val bScaled = FloatArray(3) { b[it] * 0.01f }
        val original = cosineSimilarity(a, b)
        val scaled = cosineSimilarity(aScaled, bScaled)
        assertEquals("Scaling should not change cosine similarity", original, scaled, 0.0001f)
    }
}
