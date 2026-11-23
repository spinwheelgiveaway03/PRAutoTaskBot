package com.prautotaskbot.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.prautotaskbot.MainActivity
import com.prautotaskbot.PRAutoTaskBotApp
import com.prautotaskbot.domain.queue.TaskQueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TaskMonitoringService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification("Monitoring siap"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoringLoop()
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            val app = application as PRAutoTaskBotApp
            while (isActive) {
                val state = app.taskQueueManager.getState()
                if (state == TaskQueueManager.QueueState.MONITORING ||
                    state == TaskQueueManager.QueueState.RUNNING
                ) {
                    val intervalSec = app.settingsDataStore.monitoringIntervalSec.first()
                    delay(intervalSec * 1000L)
                    if (app.taskQueueManager.currentTask.value == null) {
                        val notif = createNotification(
                            "Antrean kosong. Periksa PR.SOCIAL (interval ${intervalSec}s)"
                        )
                        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                        nm.notify(NOTIFICATION_ID + 1, notif)
                    }
                } else {
                    delay(15_000)
                }
            }
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, PRAutoTaskBotApp.CHANNEL_MONITORING)
            .setContentTitle("PR Auto Task Bot")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        job.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
    }
}
