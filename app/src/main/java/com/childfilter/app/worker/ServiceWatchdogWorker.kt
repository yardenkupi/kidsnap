package com.childfilter.app.worker

import android.content.Context
import android.content.Intent
import androidx.work.*
import com.childfilter.app.data.AppPreferences
import com.childfilter.app.service.FolderWatcherService
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ServiceWatchdogWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = AppPreferences.getInstance(applicationContext)
        val enabled = prefs.isServiceEnabled().first()
        if (!enabled) return Result.success()

        // Check if service is running
        val running = isServiceRunning()
        if (!running) {
            applicationContext.startForegroundService(
                Intent(applicationContext, FolderWatcherService::class.java)
            )
        }
        return Result.success()
    }

    private fun isServiceRunning(): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == FolderWatcherService::class.java.name }
    }

    companion object {
        const val WORK_NAME = "service_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
