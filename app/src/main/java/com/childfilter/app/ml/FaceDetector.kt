package com.childfilter.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

class FaceDetector {
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setMinFaceSize(0.1f)
        .build()
    private val detector = FaceDetection.getClient(options)

    suspend fun detectAndCrop(bitmap: Bitmap): List<Bitmap> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(image).await()
        return faces.mapNotNull { cropFace(bitmap, it.boundingBox) }
    }

    private fun cropFace(bitmap: Bitmap, box: Rect): Bitmap? {
        val pad = (box.width() * 0.2f).toInt()
        val l = (box.left - pad).coerceAtLeast(0)
        val t = (box.top - pad).coerceAtLeast(0)
        val r = (box.right + pad).coerceAtMost(bitmap.width)
        val b = (box.bottom + pad).coerceAtMost(bitmap.height)
        if (r - l <= 0 || b - t <= 0) return null
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }

    fun close() = detector.close()
}
