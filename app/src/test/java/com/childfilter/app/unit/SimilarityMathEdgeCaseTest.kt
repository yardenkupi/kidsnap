package com.childfilter.app.unit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Extended edge-case tests for cosine similarity, L2 normalization, and embedding math.
 * Pure JUnit4 — no Android or Robolectric.
 *
 * Mirrors the formulas used in FaceNetHelper and SetReferencePhotoScreen.
 */
class SimilarityMathEdgeCaseTest {

    // ── Test helpers (identical formulas to app code) ──

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

    // Mirrors SetReferencePhotoScreen averaging logic
    private fun averageAndNormalize(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty())
        val size = embeddings[0].size
        val averaged = FloatArray(size) { i ->
            embeddings.sumOf { it[i].toDouble() }.toFloat() / embeddings.size
        }
        val norm = sqrt(averaged.sumOf { (it * it).toDouble() }.toFloat())
        return if (norm == 0f) averaged else FloatArray(size) { averaged[it] / norm }
    }

    // ── 1-dimensional vector tests ──

    @Test
    fun `cosine similarity of 1-dim positive vectors is 1_0`() {
        val a = floatArrayOf(5f)
        val b = floatArrayOf(10f)
        assertEquals(1.0f, cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `cosine similarity of 1-dim positive and negative vectors is -1_0`() {
        val a = floatArrayOf(3f)
        val b = floatArrayOf(-7f)
        assertEquals(-1.0f, cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `l2Norm of 1-dim vector equals absolute value`() {
        assertEquals(5f, l2Norm(floatArrayOf(5f)), 0.0001f)
        assertEquals(3f, l2Norm(floatArrayOf(-3f)), 0.0001f)
    }

    @Test
    fun `l2Normalize of 1-dim vector gives 1 or -1`() {
        val posResult = l2Normalize(floatArrayOf(42f))
        assertEquals(1.0f, posResult[0], 0.0001f)

        val negResult = l2Normalize(floatArrayOf(-42f))
        assertEquals(-1.0f, negResult[0], 0.0001f)
    }

    // ── Large magnitude vectors ──

    @Test
    fun `cosine similarity invariant under large scaling (1e8)`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(4f, 5f, 6f)
        val aLarge = FloatArray(3) { a[it] * 1e8f }
        val bLarge = FloatArray(3) { b[it] * 1e8f }
        val original = cosineSimilarity(a, b)
        val large = cosineSimilarity(aLarge, bLarge)
        assertEquals("Scaling by 1e8 should not change cosine similarity", original, large, 0.001f)
    }

    @Test
    fun `cosine similarity invariant under very small scaling (1e-6)`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val aSmall = FloatArray(3) { a[it] * 1e-6f }
        val bSmall = FloatArray(3) { b[it] * 1e-6f }
        assertEquals(cosineSimilarity(a, b), cosineSimilarity(aSmall, bSmall), 0.0001f)
    }

    @Test
    fun `l2Norm of large-magnitude vector does not overflow`() {
        val large = FloatArray(128) { 1e18f }
        val norm = l2Norm(large)
        assertTrue("Norm should be positive and finite", norm > 0f && norm.isFinite())
    }

    // ── 512-dimensional vector tests ──

    @Test
    fun `cosine similarity of identical 512-dim vectors is 1_0`() {
        val v = FloatArray(512) { (it + 1).toFloat() * 0.001f }
        assertEquals(1.0f, cosineSimilarity(v, v), 0.0001f)
    }

    @Test
    fun `cosine similarity of orthogonal 512-dim vectors is 0_0`() {
        val a = FloatArray(512) { if (it < 256) 1f else 0f }
        val b = FloatArray(512) { if (it >= 256) 1f else 0f }
        assertEquals(0.0f, cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `l2Normalize of 512-dim all-ones vector produces unit vector`() {
        val v = FloatArray(512) { 1f }
        val normalized = l2Normalize(v)
        val norm = l2Norm(normalized)
        assertEquals(1.0f, norm, 0.0001f)
        // Each element should be 1/sqrt(512)
        val expected = 1f / sqrt(512f)
        for (x in normalized) {
            assertEquals(expected, x, 0.0001f)
        }
    }

    @Test
    fun `dot product of two 512-dim normalized vectors is in range -1 to 1`() {
        val a = l2Normalize(FloatArray(512) { (it + 1).toFloat() })
        val b = l2Normalize(FloatArray(512) { (512 - it).toFloat() })
        val dot = dotProduct(a, b)
        assertTrue("Dot product of unit vectors should be in [-1,1]", dot >= -1.0001f && dot <= 1.0001f)
    }

    // ── All-negative vectors ──

    @Test
    fun `two all-negative vectors have positive cosine similarity`() {
        val a = floatArrayOf(-1f, -2f, -3f)
        val b = floatArrayOf(-4f, -5f, -6f)
        val score = cosineSimilarity(a, b)
        assertTrue("Two all-negative same-direction vectors should have positive similarity", score > 0f)
        assertEquals(1.0f, score, 0.001f) // same direction
    }

    @Test
    fun `all-positive vs all-negative vector of same magnitude gives -1_0`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(-1f, -2f, -3f)
        assertEquals(-1.0f, cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `mixed-sign vectors similarity is in range -1 to 1`() {
        val mixed = listOf(
            floatArrayOf(1f, -1f, 1f, -1f) to floatArrayOf(-1f, 1f, -1f, 1f),
            floatArrayOf(0.5f, -0.5f, 0.3f) to floatArrayOf(-0.3f, 0.7f, 0.1f),
            floatArrayOf(-100f, 200f, -50f) to floatArrayOf(100f, -200f, 50f)
        )
        for ((a, b) in mixed) {
            val score = cosineSimilarity(a, b)
            assertTrue(
                "Cosine similarity $score should be in [-1,1]",
                score >= -1.0001f && score <= 1.0001f
            )
        }
    }

    // ── Near-parallel vectors ──

    @Test
    fun `near-parallel vectors have cosine similarity close to 1_0`() {
        val v = floatArrayOf(0.6f, 0.8f, 0.0f) // unit vector
        val nearV = floatArrayOf(0.601f, 0.799f, 0.001f) // very close
        val score = cosineSimilarity(v, nearV)
        assertTrue("Near-parallel vectors should have similarity close to 1.0", score > 0.999f)
    }

    @Test
    fun `slightly rotated vector still has high cosine similarity`() {
        // Rotate [1,0] by 5 degrees ≈ cos(5°) ≈ 0.9962
        val a = floatArrayOf(1f, 0f)
        val angle = (5.0 * Math.PI / 180.0)
        val b = floatArrayOf(Math.cos(angle).toFloat(), Math.sin(angle).toFloat())
        val score = cosineSimilarity(a, b)
        assertEquals(Math.cos(angle).toFloat(), score, 0.0001f)
        assertTrue(score > 0.99f)
    }

    // ── Embedding averaging (mirrors SetReferencePhotoScreen logic) ──

    @Test
    fun `average of single embedding then normalized is unit vector`() {
        val e = floatArrayOf(3f, 4f) // norm = 5
        val result = averageAndNormalize(listOf(e))
        assertEquals(1.0f, l2Norm(result), 0.0001f)
        assertEquals(0.6f, result[0], 0.0001f)
        assertEquals(0.8f, result[1], 0.0001f)
    }

    @Test
    fun `average of two identical embeddings equals original (then normalized)`() {
        val e = floatArrayOf(3f, 4f, 0f)
        val result = averageAndNormalize(listOf(e, e))
        // Average of e+e = e, then normalized = e/|e|
        val expected = l2Normalize(e)
        for (i in expected.indices) {
            assertEquals(expected[i], result[i], 0.0001f)
        }
    }

    @Test
    fun `average of two already-normalized identical embeddings is same normalized`() {
        val e = l2Normalize(floatArrayOf(1f, 2f, 3f))
        val result = averageAndNormalize(listOf(e, e))
        for (i in e.indices) {
            assertEquals(e[i], result[i], 0.0001f)
        }
    }

    @Test
    fun `average of opposite unit vectors is zero vector (norm=0 case)`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        val result = averageAndNormalize(listOf(a, b))
        // Average is [0,0,0], norm=0 → returns zero vector as-is
        assertEquals(0f, result[0], 0.0001f)
        assertEquals(0f, result[1], 0.0001f)
        assertEquals(0f, result[2], 0.0001f)
    }

    @Test
    fun `average of 5 embeddings is mean of each dimension`() {
        val embeddings = (1..5).map { i -> floatArrayOf(i.toFloat(), i.toFloat() * 2f) }
        // Expected average: [3.0, 6.0] (mean of 1..5 and 2..10)
        val size = embeddings[0].size
        val expectedAvg = FloatArray(size) { dim ->
            embeddings.sumOf { it[dim].toDouble() }.toFloat() / embeddings.size
        }
        val result = averageAndNormalize(embeddings)
        // The result should be in the same direction as expectedAvg
        val direction = l2Normalize(expectedAvg)
        for (i in direction.indices) {
            assertEquals("dim $i", direction[i], result[i], 0.001f)
        }
    }

    @Test
    fun `averaged and normalized result is always unit vector (various inputs)`() {
        val testCases = listOf(
            listOf(floatArrayOf(1f, 0f, 0f)),
            listOf(floatArrayOf(1f, 2f, 3f), floatArrayOf(4f, 5f, 6f)),
            (1..5).map { i -> FloatArray(128) { it.toFloat() * i * 0.01f } },
            listOf(floatArrayOf(100f, -50f, 25f), floatArrayOf(-100f, 50f, -25f), floatArrayOf(10f, 10f, 10f))
        )
        for (embeddings in testCases) {
            val result = averageAndNormalize(embeddings)
            val norm = l2Norm(result)
            // If all-zero average, norm is 0; otherwise should be unit
            if (norm > 0f) {
                assertEquals("Averaged result should be unit vector", 1.0f, norm, 0.0001f)
            }
        }
    }

    @Test
    fun `averaging is order-independent for two embeddings`() {
        val a = floatArrayOf(0.3f, 0.5f, 0.8f)
        val b = floatArrayOf(0.7f, 0.2f, 0.4f)
        val ab = averageAndNormalize(listOf(a, b))
        val ba = averageAndNormalize(listOf(b, a))
        for (i in ab.indices) {
            assertEquals("dim $i", ab[i], ba[i], 0.0001f)
        }
    }

    @Test
    fun `averaged and normalized result has same direction as naive sum`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val result = averageAndNormalize(listOf(a, b))
        // Average of [1,0] and [0,1] is [0.5, 0.5] → normalized to [1/√2, 1/√2]
        val expected = 1f / sqrt(2f)
        assertEquals(expected, result[0], 0.0001f)
        assertEquals(expected, result[1], 0.0001f)
        assertEquals(0f, result[2], 0.0001f)
    }

    // ── L2 norm edge cases ──

    @Test
    fun `l2Norm of 512-dim all-ones vector is sqrt(512)`() {
        val v = FloatArray(512) { 1f }
        val expected = sqrt(512f)
        assertEquals(expected, l2Norm(v), 0.01f)
    }

    @Test
    fun `l2Norm of vector with one non-zero element equals that element's absolute value`() {
        val v = FloatArray(100) { 0f }.also { it[42] = 7f }
        assertEquals(7f, l2Norm(v), 0.0001f)
    }

    @Test
    fun `l2Normalize of vector with single non-zero element gives 1 at that position`() {
        val v = FloatArray(100) { 0f }.also { it[42] = 7f }
        val normalized = l2Normalize(v)
        for (i in normalized.indices) {
            if (i == 42) assertEquals(1.0f, normalized[i], 0.0001f)
            else assertEquals(0.0f, normalized[i], 0.0001f)
        }
    }

    // ── Cosine similarity does not satisfy triangle inequality ──

    @Test
    fun `cosine similarity is not transitive - A similar B and B similar C does not imply A similar C`() {
        // Classic counterexample: rotate by 45° each step
        val angle45 = (45.0 * Math.PI / 180.0)
        val angle90 = (90.0 * Math.PI / 180.0)
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(Math.cos(angle45).toFloat(), Math.sin(angle45).toFloat())
        val c = floatArrayOf(Math.cos(angle90).toFloat(), Math.sin(angle90).toFloat())

        val simAB = cosineSimilarity(a, b) // cos(45°) ≈ 0.707
        val simBC = cosineSimilarity(b, c) // cos(45°) ≈ 0.707
        val simAC = cosineSimilarity(a, c) // cos(90°) = 0.0

        assertTrue("sim(A,B) should be high", simAB > 0.7f)
        assertTrue("sim(B,C) should be high", simBC > 0.7f)
        assertTrue("sim(A,C) should be low (not transitive)", simAC < 0.01f)
    }

    // ── Dot product edge cases ──

    @Test
    fun `dot product of 512-dim all-ones vectors equals 512`() {
        val v = FloatArray(512) { 1f }
        assertEquals(512f, dotProduct(v, v), 0.001f)
    }

    @Test
    fun `dot product of orthogonal 512-dim vectors is 0`() {
        val a = FloatArray(512) { if (it < 256) 1f else 0f }
        val b = FloatArray(512) { if (it >= 256) 1f else 0f }
        assertEquals(0f, dotProduct(a, b), 0.0001f)
    }

    @Test
    fun `dot product is bilinear - scalar multiple commutes`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(4f, 5f, 6f)
        val k = 7f
        val kaTimesB = dotProduct(FloatArray(3) { a[it] * k }, b)
        val kTimesADotB = dotProduct(a, b) * k
        assertEquals(kTimesADotB, kaTimesB, 0.001f)
    }

    // ── Threshold sensitivity tests ──

    @Test
    fun `vectors at exactly the similarity threshold boundary`() {
        // Create two vectors with known cosine similarity = 0.75
        // a = [1,0,0], b = [cos(θ), sin(θ), 0] where cos(θ) = 0.75
        val cosTheta = 0.75f
        val sinTheta = sqrt(1f - cosTheta * cosTheta)
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(cosTheta, sinTheta, 0f)
        val score = cosineSimilarity(a, b)
        assertEquals(0.75f, score, 0.001f)
    }

    @Test
    fun `score just above threshold should match, just below should not`() {
        val threshold = 0.75f
        val cosTheta = 0.75f
        val sinTheta = sqrt(1f - cosTheta * cosTheta)
        val reference = floatArrayOf(1f, 0f, 0f)
        val atBoundary = floatArrayOf(cosTheta, sinTheta, 0f)
        val score = cosineSimilarity(reference, atBoundary)
        // At boundary: should match (>= threshold)
        assertTrue("Score at boundary should be >= threshold", score >= threshold - 0.001f)

        // Slightly below threshold
        val slightlyBelow = floatArrayOf(0.74f, sqrt(1f - 0.74f * 0.74f), 0f)
        val belowScore = cosineSimilarity(reference, slightlyBelow)
        assertTrue("Score slightly below threshold should be < threshold", belowScore < threshold)
    }
}
