package com.childfilter.app.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Result of comparing two face embeddings.
 * @param score cosine similarity in [0, 1]
 * @param confidence human-readable bucket: "High", "Medium", or "Low"
 */
data class MatchResult(val score: Float, val confidence: String)

class FaceNetHelper(context: Context) {

    private var interpreter: Interpreter? = null
    var isModelLoaded = false
        private set

    init {
        try {
            interpreter = Interpreter(
                FileUtil.loadMappedFile(context, "facenet.tflite"),
                Interpreter.Options().apply { setNumThreads(2) }
            )
            isModelLoaded = true
        } catch (e: Exception) {
            isModelLoaded = false
        }
    }

    /** Compute the L2-normalized FaceNet embedding for a single face bitmap. */
    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        val interp = interpreter ?: throw IllegalStateException("Face recognition model not loaded. Please contact support.")
        val inputShape = interp.getInputTensor(0).shape() // [1, H, W, 3]
        val inputSize = inputShape[1]
        val input = preprocessFace(faceBitmap, inputSize)
        val embeddingSize = interp.getOutputTensor(0).shape()[1]
        val output = Array(1) { FloatArray(embeddingSize) }
        interp.run(input, output)
        return l2Normalize(output[0])
    }

    /**
     * Extract embeddings for ALL detected faces in a bitmap.
     * Returns a list of (Face, embedding) pairs, one per detected face.
     * The [FaceDetector] is used internally to detect and crop each face.
     */
    suspend fun extractAllFaceEmbeddings(bitmap: Bitmap, faceDetector: FaceDetector): List<Pair<Face, FloatArray>> {
        val faceCrops = faceDetector.detectFaces(bitmap)
        return faceCrops.map { (face, crop) ->
            face to getEmbedding(crop)
        }
    }

    /**
     * Extract a single embedding from the largest face in the bitmap.
     * Preferred for reference / enrollment photos where only one child should appear.
     * Returns null if no face is detected.
     */
    suspend fun extractLargestFaceEmbedding(bitmap: Bitmap, faceDetector: FaceDetector): FloatArray? {
        val crop = faceDetector.detectLargestFace(bitmap) ?: return null
        return getEmbedding(crop)
    }

    fun isSamePerson(e1: FloatArray, e2: FloatArray, threshold: Float = 0.75f) =
        cosineSimilarity(e1, e2) >= threshold

    fun similarityScore(e1: FloatArray, e2: FloatArray) = cosineSimilarity(e1, e2)

    /**
     * Compare two embeddings and return a [MatchResult] with both the raw score
     * and a human-readable confidence level.
     */
    fun compareWithConfidence(e1: FloatArray, e2: FloatArray): MatchResult {
        val score = cosineSimilarity(e1, e2)
        val confidence = when {
            score > 0.7f -> "High"
            score >= 0.5f -> "Medium"
            else -> "Low"
        }
        return MatchResult(score, confidence)
    }

    private fun preprocessFace(bitmap: Bitmap, size: Int): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val buf = ByteBuffer.allocateDirect(1 * size * size * 3 * 4).apply { order(ByteOrder.nativeOrder()) }
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        for (px in pixels) {
            buf.putFloat(((px shr 16 and 0xFF) - 127.5f) / 127.5f)
            buf.putFloat(((px shr 8 and 0xFF) - 127.5f) / 127.5f)
            buf.putFloat(((px and 0xFF) - 127.5f) / 127.5f)
        }
        buf.rewind()
        return buf
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.sumOf { (it * it).toDouble() }.toFloat())
        return if (norm == 0f) v else FloatArray(v.size) { v[it] / norm }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i] }
        val d = sqrt(na) * sqrt(nb)
        return if (d == 0f) 0f else dot / d
    }

    fun close() = interpreter?.close()
}
