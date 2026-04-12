package com.childfilter.app.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2

class FaceDetector {
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setMinFaceSize(0.1f)
        .build()
    private val detector = FaceDetection.getClient(options)

    /** Detect faces and return cropped, aligned bitmaps (legacy API). */
    suspend fun detectAndCrop(bitmap: Bitmap): List<Bitmap> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(image).await()
        return faces.mapNotNull { face -> alignAndCrop(bitmap, face) }
    }

    /**
     * Detect all faces and return each [Face] paired with its aligned, cropped bitmap.
     * Useful for multi-face matching in group photos.
     */
    suspend fun detectFaces(bitmap: Bitmap): List<Pair<Face, Bitmap>> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(image).await()
        return faces.mapNotNull { face ->
            alignAndCrop(bitmap, face)?.let { crop -> face to crop }
        }
    }

    /**
     * Detect faces and return the single largest face crop (by bounding box area).
     * Returns null if no face is detected.
     */
    suspend fun detectLargestFace(bitmap: Bitmap): Bitmap? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(image).await()
        val largest = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return null
        return alignAndCrop(bitmap, largest)
    }

    /**
     * Detect faces and return all [Face] objects along with the source bitmap dimensions.
     * Used by the UI to compute face-area ratios for quality feedback.
     */
    suspend fun detectFacesRaw(bitmap: Bitmap): List<Face> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return detector.process(image).await()
    }

    /**
     * Align the face using eye landmarks (if available) and crop with 25% padding.
     */
    private fun alignAndCrop(bitmap: Bitmap, face: Face): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        val srcBitmap = if (leftEye != null && rightEye != null) {
            // Compute the angle between eyes and rotate to align horizontally
            val dx = rightEye.x - leftEye.x
            val dy = rightEye.y - leftEye.y
            val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

            if (kotlin.math.abs(angleDeg) > 0.5f) {
                val matrix = Matrix()
                matrix.postRotate(-angleDeg, bitmap.width / 2f, bitmap.height / 2f)
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } else {
            bitmap
        }

        return cropFace(srcBitmap, face.boundingBox)
    }

    private fun cropFace(bitmap: Bitmap, box: Rect): Bitmap? {
        // Use 25% padding for better context around the face
        val padX = (box.width() * 0.25f).toInt()
        val padY = (box.height() * 0.25f).toInt()
        val l = (box.left - padX).coerceAtLeast(0)
        val t = (box.top - padY).coerceAtLeast(0)
        val r = (box.right + padX).coerceAtMost(bitmap.width)
        val b = (box.bottom + padY).coerceAtMost(bitmap.height)
        if (r - l <= 0 || b - t <= 0) return null
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }

    fun close() = detector.close()
}
