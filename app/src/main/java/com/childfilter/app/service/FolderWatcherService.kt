package com.childfilter.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.childfilter.app.MainActivity
import com.childfilter.app.data.AppPreferences
import com.childfilter.app.data.LogEntry
import com.childfilter.app.ml.FaceDetector
import com.childfilter.app.ml.FaceNetHelper
import com.childfilter.app.util.ImageSaver
import com.childfilter.app.worker.ServiceWatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class FolderWatcherService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceNetHelper: FaceNetHelper
    private lateinit var prefs: AppPreferences
    private var fileObserver: FileObserver? = null

    companion object {
        const val CHANNEL_ID = "watcher_channel"
        const val MATCH_CHANNEL_ID = "match_channel"
        const val ERROR_CHANNEL_ID = "error_channel"
        const val NOTIFICATION_ID = 1
        const val ERROR_NOTIFICATION_ID = 2
        private const val TAG = "FolderWatcher"
        private const val GROUP_WINDOW_MS = 300000L  // 5 minutes
        private val WHATSAPP_PATHS = listOf(
            "${Environment.getExternalStorageDirectory()}/WhatsApp/Media/WhatsApp Images",
            "${Environment.getExternalStorageDirectory()}/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"
        )
    }

    override fun onCreate() {
        super.onCreate()
        faceDetector = FaceDetector()
        faceNetHelper = FaceNetHelper(this)
        prefs = AppPreferences.getInstance(this)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Schedule watchdog so it can revive us if the OS kills us
        ServiceWatchdogWorker.schedule(this)

        // Guard: READ_MEDIA_IMAGES (or READ_EXTERNAL_STORAGE on older APIs) must be granted
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            startForeground(NOTIFICATION_ID, buildNotification("Waiting for permissions..."))
            showErrorNotification(
                title = "Storage permission required",
                text = "Grant storage/media permission so Child Photo Filter can watch WhatsApp photos. Tap to open the app."
            )
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Watching for new photos..."))
        startWatching()
        return START_STICKY
    }

    private fun startWatching() {
        val dir = WHATSAPP_PATHS.map { File(it) }.firstOrNull { it.exists() }

        if (dir == null) {
            Log.w(TAG, "WhatsApp Images folder not found in any known path")
            // Update foreground notification to reflect the problem
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification("WhatsApp folder not found"))
            showErrorNotification(
                title = "WhatsApp folder not found",
                text = "Make sure WhatsApp is installed and has received at least one image. Tap to open settings."
            )
            return
        }

        Log.i(TAG, "Watching: ${dir.absolutePath}")

        // Update the foreground notification to show the active path
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification("Watching: ${dir.name}"))

        fileObserver = object : FileObserver(dir, CREATE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null && isImageFile(path)) {
                    scope.launch { processImage(File(dir, path)) }
                }
            }
        }
        fileObserver!!.startWatching()
    }

    private suspend fun processImage(file: File) {
        try {
            delay(500)

            // (a) Group filtering: if selected groups exist, check last active group
            val selectedGroups = prefs.getSelectedGroups().first()
            if (selectedGroups.isNotEmpty()) {
                val (lastGroup, lastTime) = prefs.getLastActiveGroup().first()
                val timeDiff = System.currentTimeMillis() - lastTime
                if (!selectedGroups.contains(lastGroup) || timeDiff > GROUP_WINDOW_MS) {
                    Log.d(TAG, "Skipping: group='$lastGroup' not selected or outside window (${timeDiff}ms)")
                    return
                }
            }

            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val threshold = prefs.getThreshold().first()
            val faces = faceDetector.detectAndCrop(bitmap)

            // Track every processed image
            prefs.incrementProcessed()

            // (b) Multi-child matching: use children list if available, otherwise legacy single embedding
            val children = prefs.getChildren().first()

            var matchFound = false

            if (children.isNotEmpty()) {
                // Match against any registered child
                for (face in faces) {
                    val embedding = faceNetHelper.getEmbedding(face)
                    for (child in children) {
                        val score = faceNetHelper.similarityScore(embedding, child.embedding)
                        if (score >= threshold) {
                            Log.i(TAG, "Match! child=${child.name} score=$score file=${file.name}")
                            val savedUri = ImageSaver.saveToAlbum(this@FolderWatcherService, bitmap)
                            prefs.incrementMatched()
                            // Record which child matched this saved photo
                            val savedName = "child_${System.currentTimeMillis()}.jpg"
                            prefs.addMatchedPhotoRecord(savedName, child.name)
                            notifyMatch(file.name, score, child.name, face)
                            prefs.addLogEntry(LogEntry(
                                timestamp = System.currentTimeMillis(),
                                type = "match",
                                details = "Match: ${child.name} found in ${file.name}",
                                childName = child.name
                            ))
                            matchFound = true
                            return
                        }
                    }
                }
            } else {
                // Legacy: single reference embedding
                val refEmbedding = prefs.getEmbedding().first() ?: return
                for (face in faces) {
                    val embedding = faceNetHelper.getEmbedding(face)
                    val score = faceNetHelper.similarityScore(embedding, refEmbedding)
                    if (score >= threshold) {
                        Log.i(TAG, "Match! score=$score file=${file.name}")
                        val savedUri = ImageSaver.saveToAlbum(this@FolderWatcherService, bitmap)
                        prefs.incrementMatched()
                        // Record matched photo (no specific child in legacy mode)
                        val savedName = "child_${System.currentTimeMillis()}.jpg"
                        prefs.addMatchedPhotoRecord(savedName, null)
                        notifyMatch(file.name, score, null, face)
                        prefs.addLogEntry(LogEntry(
                            timestamp = System.currentTimeMillis(),
                            type = "match",
                            details = "Match found in ${file.name}",
                            childName = null
                        ))
                        matchFound = true
                        break
                    }
                }
            }

            // No-match scans are not logged to keep the activity log signal-to-noise ratio high
        } catch (e: Exception) {
            Log.e(TAG, "Error processing ${file.name}", e)
            prefs.addLogEntry(LogEntry(
                timestamp = System.currentTimeMillis(),
                type = "error",
                details = e.message ?: "Unknown error processing ${file.name}"
            ))
        }
    }

    private fun notifyMatch(
        fileName: String,
        similarity: Float,
        childName: String?,
        faceBitmap: android.graphics.Bitmap?
    ) {
        // Check if notifications are enabled
        val notificationsEnabled = kotlinx.coroutines.runBlocking {
            prefs.getNotificationsEnabled().first()
        }
        if (!notificationsEnabled) return

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "matched_photos")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = childName ?: "your child"
        val builder = NotificationCompat.Builder(this, MATCH_CHANNEL_ID)
            .setContentTitle("\uD83D\uDCF8 Photo saved!")
            .setContentText("Found $displayName in a WhatsApp photo \u2014 tap to view")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (faceBitmap != null) {
            builder.setLargeIcon(faceBitmap)
        }

        nm.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun showErrorNotification(title: String, text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            ERROR_NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ERROR_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(ERROR_NOTIFICATION_ID, notification)
    }

    private fun isImageFile(name: String) = name.lowercase().let {
        it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            NotificationChannel(CHANNEL_ID, "Photo Watcher", NotificationManager.IMPORTANCE_LOW)
                .also {
                    it.description = "Monitors WhatsApp for your child's photos"
                    nm.createNotificationChannel(it)
                }
            NotificationChannel(MATCH_CHANNEL_ID, "Match Found!", NotificationManager.IMPORTANCE_HIGH)
                .also {
                    it.description = "Alerts when a matching child photo is found"
                    nm.createNotificationChannel(it)
                }
            NotificationChannel(ERROR_CHANNEL_ID, "Errors", NotificationManager.IMPORTANCE_HIGH)
                .also {
                    it.description = "Alerts when the watcher encounters a configuration problem"
                    nm.createNotificationChannel(it)
                }
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Child Photo Filter")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fileObserver?.stopWatching()
        scope.cancel()
        faceDetector.close()
        faceNetHelper.close()
        // Intentionally do NOT cancel the watchdog — it will restart us if needed
        super.onDestroy()
    }
}
