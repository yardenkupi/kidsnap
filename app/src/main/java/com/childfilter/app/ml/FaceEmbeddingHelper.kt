package com.childfilter.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceEmbeddingHelper(private val context: Context) {

    companion object {
        private const val MODEL_FILE = "facenet.tflite"
        private const val INPUT_SIZE = 160
        private const val EMBEDDING_SIZE = 128
    }

    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setMinFaceSize(0.15f)
        .build()

    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    private val interpreter: Interpreter? by lazy {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            Interpreter(modelBuffer)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun detectAndEmbed(bitmap: Bitmap): List<FloatArray> {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val faces = faceDetector.process(inputImage).await()

        return faces.mapNotNull { face ->
            val bounds = face.boundingBox
            val croppedFace = cropFace(bitmap, bounds) ?: return@mapNotNull null
            getEmbedding(croppedFace)
        }
    }

    private fun cropFace(bitmap: Bitmap, bounds: Rect): Bitmap? {
        val left = bounds.left.coerceAtLeast(0)
        val top = bounds.top.coerceAtLeast(0)
        val right = bounds.right.coerceAtMost(bitmap.width)
        val bottom = bounds.bottom.coerceAtMost(bitmap.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null

        val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
        return Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)
    }

    private fun getEmbedding(faceBitmap: Bitmap): FloatArray? {
        val tflite = interpreter ?: return null
        val inputBuffer = bitmapToByteBuffer(faceBitmap)
        val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
        tflite.run(inputBuffer, output)
        return output[0]
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            // Normalize to [-1, 1]
            buffer.putFloat((r - 0.5f) / 0.5f)
            buffer.putFloat((g - 0.5f) / 0.5f)
            buffer.putFloat((b - 0.5f) / 0.5f)
        }
        buffer.rewind()
        return buffer
    }
}
