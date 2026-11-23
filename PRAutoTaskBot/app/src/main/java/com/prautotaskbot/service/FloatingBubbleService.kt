package com.prautotaskbot.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.prautotaskbot.MainActivity
import com.prautotaskbot.PRAutoTaskBotApp
import com.prautotaskbot.domain.queue.TaskQueueManager
import com.prautotaskbot.integration.PrSocialHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var expandedView: View? = null
    private var isExpanded = false

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var queueManager: TaskQueueManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as PRAutoTaskBotApp
        queueManager = app.taskQueueManager
        startForeground(NOTIFICATION_ID, createNotification("Bubble aktif"))
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, PRAutoTaskBotApp.CHANNEL_BUBBLE)
            .setContentTitle("PR Auto Task Bot")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showBubble() {
        val bubble = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setBackgroundColor(statusColor())
            setPadding(28, 28, 28, 28)
            alpha = 0.92f
        }
        bubbleView = bubble

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 220
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (kotlin.math.abs(event.rawX - initialTouchX) < 12 &&
                        kotlin.math.abs(event.rawY - initialTouchY) < 12
                    ) {
                        toggleExpanded()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(bubble, params)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal bubble: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun statusColor(): Int = when (queueManager.getState()) {
        TaskQueueManager.QueueState.RUNNING -> 0xFF4CAF50.toInt()
        TaskQueueManager.QueueState.PAUSED -> 0xFFFFC107.toInt()
        TaskQueueManager.QueueState.STOPPED -> 0xFFF44336.toInt()
        TaskQueueManager.QueueState.MONITORING -> 0xFF2196F3.toInt()
        else -> 0xFF9E9E9E.toInt()
    }

    private fun updateBubbleColor() {
        (bubbleView as? ImageView)?.setBackgroundColor(statusColor())
    }

    private fun toggleExpanded() {
        if (isExpanded) hideExpanded() else showExpanded()
    }

    private fun showExpanded() {
        if (expandedView != null) return
        isExpanded = true

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF01A1A1A.toInt())
            setPadding(40, 28, 40, 28)
        }

        fun addBtn(label: String, action: () -> Unit) {
            val tv = TextView(this).apply {
                text = label
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 15f
                setPadding(12, 18, 12, 18)
                setOnClickListener { action() }
            }
            panel.addView(tv)
        }

        val current = queueManager.currentTask.value
        val stateText = "Status: ${queueManager.getState()}"
        val taskText = if (current != null) {
            "${current.type} @${current.targetUsername ?: "?"}"
        } else "Tidak ada tugas aktif"

        addBtn(stateText) {}
        addBtn(taskText) {}
        addBtn("──────────") {}

        addBtn("▶ START") {
            scope.launch {
                queueManager.start()
                updateBubbleColor()
                Toast.makeText(this@FloatingBubbleService, "Antrean dimulai", Toast.LENGTH_SHORT).show()
                hideExpanded()
            }
        }
        addBtn("⏸ PAUSE") {
            scope.launch {
                queueManager.pause()
                updateBubbleColor()
                Toast.makeText(this@FloatingBubbleService, "Dijeda", Toast.LENGTH_SHORT).show()
                hideExpanded()
            }
        }
        addBtn("⏹ STOP") {
            scope.launch {
                queueManager.stop()
                updateBubbleColor()
                Toast.makeText(this@FloatingBubbleService, "Dihentikan", Toast.LENGTH_SHORT).show()
                hideExpanded()
            }
        }
        addBtn("✅ SELESAI (tugas ini)") {
            scope.launch {
                queueManager.markCurrentCompleted("Dikonfirmasi pengguna")
                updateBubbleColor()
                Toast.makeText(this@FloatingBubbleService, "Ditandai selesai", Toast.LENGTH_SHORT).show()
                hideExpanded()
            }
        }
        addBtn("❌ GAGAL") {
            scope.launch {
                queueManager.markCurrentFailed("Gagal verifikasi")
                updateBubbleColor()
                hideExpanded()
            }
        }
        addBtn("🔗 BUKA TARGET") {
            val task = queueManager.currentTask.value
            val url = task?.targetUrl
            if (url != null) {
                PrSocialHelper.openUrl(this, url)
            } else {
                Toast.makeText(this, "Tidak ada URL target", Toast.LENGTH_SHORT).show()
            }
            hideExpanded()
        }
        addBtn("🌐 BUKA PR.SOCIAL") {
            PrSocialHelper.openPrSocial(this)
            hideExpanded()
        }
        addBtn("⚙ BUKA APLIKASI") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            hideExpanded()
        }
        addBtn("✕ Tutup Panel") { hideExpanded() }

        expandedView = panel
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        try {
            windowManager?.addView(panel, params)
        } catch (_: Exception) {
            isExpanded = false
            expandedView = null
        }
    }

    private fun hideExpanded() {
        expandedView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        expandedView = null
        isExpanded = false
    }

    override fun onDestroy() {
        serviceJob.cancel()
        hideExpanded()
        bubbleView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
