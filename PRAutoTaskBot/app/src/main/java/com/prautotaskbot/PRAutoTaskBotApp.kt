package com.prautotaskbot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.prautotaskbot.data.db.AppDatabase
import com.prautotaskbot.data.repository.TaskRepository
import com.prautotaskbot.data.datastore.SettingsDataStore
import com.prautotaskbot.domain.queue.TaskQueueManager
import com.prautotaskbot.platform.InstagramAdapter
import com.prautotaskbot.platform.PlatformAdapter

class PRAutoTaskBotApp : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val taskRepository by lazy { TaskRepository(database.taskDao()) }
    val settingsDataStore by lazy { SettingsDataStore(this) }
    val taskQueueManager by lazy { TaskQueueManager(taskRepository) }

    val adapters: Map<String, PlatformAdapter> by lazy {
        mapOf("instagram" to InstagramAdapter())
    }

    fun getAdapter(platformId: String): PlatformAdapter? = adapters[platformId]

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val bubbleChannel = NotificationChannel(
                CHANNEL_BUBBLE,
                "Floating Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Status floating control bubble" }

            val monitoringChannel = NotificationChannel(
                CHANNEL_MONITORING,
                "Task Monitoring",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Pemberitahuan tugas baru dan status antrean" }

            manager.createNotificationChannel(bubbleChannel)
            manager.createNotificationChannel(monitoringChannel)
        }
    }

    companion object {
        const val CHANNEL_BUBBLE = "bubble_channel"
        const val CHANNEL_MONITORING = "monitoring_channel"
    }
}
