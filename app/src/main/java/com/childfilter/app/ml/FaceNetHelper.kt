package com.childfilter.app.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

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

    fun isSamePerson(e1: FloatArray, e2: FloatArray, threshold: Float = 0.75f) =
        cosineSimilarity(e1, e2) >= threshold

    fun similarityScore(e1: FloatArray, e2: FloatArray) = cosineSimilarity(e1, e2)

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
