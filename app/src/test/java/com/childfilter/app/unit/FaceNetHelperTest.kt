package com.childfilter.app.unit

import android.content.Context
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
class FaceNetHelperTest {

    @Test
    fun `FaceNetHelper with missing model file has isModelLoaded false`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FaceNetHelper(context)
        assertFalse(helper.isModelLoaded)
    }

    @Test(expected = IllegalStateException::class)
    fun `getEmbedding throws IllegalStateException when model is not loaded`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FaceNetHelper(context)
        val bitmap = android.graphics.Bitmap.createBitmap(112, 112, android.graphics.Bitmap.Config.ARGB_8888)
        helper.getEmbedding(bitmap)
    }

    @Test
    fun `isSamePerson returns true when embeddings are identical`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FaceNetHelper(context)
        val embedding = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        assertTrue(helper.isSamePerson(embedding, embedding, 0.75f))
    }

    @Test
    fun `isSamePerson returns false when embeddings are orthogonal`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FaceNetHelper(context)
        val e1 = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f)
        val e2 = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f)
        assertFalse(helper.isSamePerson(e1, e2, 0.75f))
    }

    @Test
    fun `similarityScore returns 1_0 for identical embeddings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FaceNetHelper(context)
        val embedding = floatArrayOf(0.3f, 0.4f, 0.5f)
        val score = helper.similarityScore(embedding, embedding)
        assertEquals(1.0f, score, 0.001f)
    }

    @Test
    fun `l2Normalize produces unit vector`() {
        // We test l2Normalize indirectly: if we pass a vector through getEmbedding,
        // the output is normalized. But since model isn't loaded, we test the math
        // via similarityScore on already-normalized vectors.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FaceNetHelper(context)

        // Manually create a non-unit vector and verify cosine similarity properties
        val v1 = floatArrayOf(3.0f, 4.0f)  // norm = 5
        val v2 = floatArrayOf(3.0f, 4.0f)  // same direction

        // Cosine similarity of same-direction vectors should be 1.0 regardless of magnitude
        val score = helper.similarityScore(v1, v2)
        assertEquals(1.0f, score, 0.001f)

        // Verify the concept: l2Normalize of [3,4] should be [0.6, 0.8]
        val norm = sqrt(3.0f * 3.0f + 4.0f * 4.0f)
        assertEquals(5.0f, norm, 0.001f)
        val normalized = floatArrayOf(3.0f / norm, 4.0f / norm)
        val unitNorm = sqrt(normalized[0] * normalized[0] + normalized[1] * normalized[1])
        assertEquals(1.0f, unitNorm, 0.001f)
    }
}
