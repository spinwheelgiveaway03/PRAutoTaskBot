package com.prautotaskbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Bot otomatis untuk tugas "Ikuti di Instagram" di PR.SOCIAL.
 * Berjalan di HP kamu sendiri lewat Accessibility Service, memakai akun kamu sendiri.
 */
class AutomationService : AccessibilityService() {

    private enum class Phase { LIST, IN_INSTAGRAM, IG_CLICKED, RETURNING, CHECKING }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var loopJob: Job? = null

    private var windowManager: WindowManager? = null
    private var overlay: LinearLayout? = null
    private var toggleView: TextView? = null
    private var statusView: TextView? = null

    private var enabled = false
    private var phase = Phase.LIST
    private var phaseSince = 0L
    private var browserPkg: String? = null
    private var currentUser: String? = null
    private var checkAttempts = 0
    private var igRetries = 0
    private var triedDirect = false
    private var idleTicks = 0
    private var nextAllowedAt = 0L
    private var doneCount = 0
    private val doneTimes = ArrayList<Long>()
    private var statusText = "Siap. Tekan tombol untuk mulai."

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        startLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        stopAll()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    private fun stopAll() {
        loopJob?.cancel()
        job.cancel()
        overlay?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }

    // ---------------------------------------------------------------- UI

    private fun showOverlay() {
        val toggle = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(28, 18, 28, 18)
            setOnClickListener { toggleEnabled() }
        }
        val status = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(16, 8, 16, 8)
            maxWidth = 520
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toggle)
            addView(status)
        }
        toggleView = toggle
        statusView = status
        overlay = box

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 260
        }
        try {
            windowManager?.addView(box, params)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal tampilkan tombol: ${e.message}", Toast.LENGTH_LONG).show()
        }
        refreshUi()
    }

    private fun toggleEnabled() {
        enabled = !enabled
        if (enabled) {
            resetTask()
            nextAllowedAt = 0L
            setStatus("Bot jalan. Buka halaman Tugas PR.SOCIAL.")
        } else {
            setStatus("Bot dimatikan.")
        }
    }

    private fun refreshUi() {
        toggleView?.apply {
            text = if (enabled) "🤖 AUTO: ON" else "🤖 AUTO: OFF"
            setBackgroundColor(if (enabled) 0xEE2E7D32.toInt() else 0xEEC62828.toInt())
        }
        statusView?.text = "$statusText\n✔ $doneCount selesai | ${recentCount(System.currentTimeMillis())}/$MAX_PER_HOUR per jam"
    }

    private fun setStatus(s: String) {
        statusText = s
        refreshUi()
    }

    // ---------------------------------------------------------------- Loop

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                delay(Random.nextLong(1500, 3500))
                if (!enabled) continue
                try {
                    tick()
                } catch (e: Exception) {
                    setStatus("Error: ${e.message}")
                }
            }
        }
    }

    private fun tick() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        val now = System.currentTimeMillis()

        if (pkg == packageName) return
        if (pkg == IG_PACKAGE) {
            handleInstagram(root, now)
            return
        }
        if (isPrSocial(root)) {
            browserPkg = pkg
            handlePr(root, now)
        }
    }

    // ---------------------------------------------------------------- Instagram

    private fun handleInstagram(root: AccessibilityNodeInfo, now: Long) {
        if (isBlocked(root)) {
            enabled = false
            setStatus("⛔ Instagram membatasi akun. Bot dimatikan, istirahat dulu.")
            Toast.makeText(this, "Instagram membatasi aksi. Bot dimatikan.", Toast.LENGTH_LONG).show()
            return
        }
        val already = findExact(root, "Mengikuti", "Following", "Diminta", "Requested")
        val btn = findExact(root, "Ikuti", "Follow", "Ikuti balik", "Follow back")

        when (phase) {
            Phase.IN_INSTAGRAM -> {
                if (already != null) {
                    returnToBrowser(now, "Sudah mengikuti")
                } else if (btn != null) {
                    clickNode(btn)
                    phase = Phase.IG_CLICKED
                    phaseSince = now
                    igRetries = 0
                    setStatus("Menekan Ikuti…")
                } else if (now - phaseSince > 20000) {
                    returnToBrowser(now, "Tombol Ikuti tidak ketemu")
                } else {
                    setStatus("Menunggu profil Instagram…")
                }
            }
            Phase.IG_CLICKED -> {
                if (already != null) {
                    returnToBrowser(now, "Berhasil mengikuti")
                } else if (now - phaseSince > 6000) {
                    if (btn != null && igRetries < 1) {
                        igRetries++
                        clickNode(btn)
                        phaseSince = now
                    } else {
                        returnToBrowser(now, "Lanjut verifikasi")
                    }
                }
            }
            Phase.RETURNING -> {
                if (now - phaseSince > 3500) {
                    launchBrowser()
                    phaseSince = now
                }
            }
            else -> {}
        }
    }

    private fun returnToBrowser(now: Long, msg: String) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        phase = Phase.RETURNING
        phaseSince = now
        setStatus("$msg → kembali ke PR.SOCIAL")
    }

    private fun isBlocked(root: AccessibilityNodeInfo): Boolean {
        val keys = listOf(
            "Coba lagi nanti", "Try again later", "Tindakan diblokir", "Action blocked",
            "membatasi aktivitas", "We limit how often"
        )
        return keys.any { findAll(root, it).isNotEmpty() }
    }

    // ---------------------------------------------------------------- PR.SOCIAL

    private fun isPrSocial(root: AccessibilityNodeInfo): Boolean {
        val keys = listOf("Periksa follow saya", "Ikuti di Instagram", "PR.SOCIAL")
        return keys.any { findAll(root, it).isNotEmpty() }
    }

    private fun handlePr(root: AccessibilityNodeInfo, now: Long) {
        val check = findFirst(root, "Periksa follow saya")

        if (check == null) {
            val start = findStartButton(root)
            if (start != null) {
                clickNode(start)
                phase = Phase.LIST
                phaseSince = now
                setStatus("Menekan Mulai…")
                return
            }
            // Jendela tugas tidak terbuka
            when (phase) {
                Phase.CHECKING -> {
                    onTaskDone(now)
                    return
                }
                Phase.LIST -> listStep(root, now)
                else -> {
                    if (now - phaseSince > 15000) {
                        resetTask()
                    } else {
                        setStatus("Menunggu halaman PR.SOCIAL…")
                    }
                }
            }
            return
        }

        // Jendela tugas terbuka
        when (phase) {
            Phase.LIST -> {
                val link = findFirst(root, "instagram.com/")
                val linkText = link?.text?.toString() ?: ""
                currentUser = if (linkText.contains("…") || linkText.contains("...")) null
                else Regex("instagram\\.com/([A-Za-z0-9._]+)").find(linkText)?.groupValues?.get(1)
                val ok = link != null && clickNode(link)
                if (!ok) openInstagramDirect()
                phase = Phase.IN_INSTAGRAM
                phaseSince = now
                checkAttempts = 0
                igRetries = 0
                triedDirect = false
                setStatus("Membuka profil Instagram…")
            }
            Phase.IN_INSTAGRAM -> {
                if (now - phaseSince > 40000) {
                    cancelTask(root, now)
                } else if (now - phaseSince > 8000 && !triedDirect) {
                    triedDirect = true
                    openInstagramDirect()
                }
            }
            Phase.IG_CLICKED, Phase.RETURNING -> {
                clickNode(check)
                phase = Phase.CHECKING
                phaseSince = now
                checkAttempts = 1
                setStatus("Menekan 'Periksa follow saya'…")
            }
            Phase.CHECKING -> {
                if (now - phaseSince > 9000) {
                    if (checkAttempts >= 3) {
                        cancelTask(root, now)
                    } else {
                        clickNode(check)
                        checkAttempts++
                        phaseSince = now
                        setStatus("Coba periksa lagi ($checkAttempts/3)")
                    }
                }
            }
        }
    }

    private fun listStep(root: AccessibilityNodeInfo, now: Long) {
        if (now < nextAllowedAt) {
            setStatus("Jeda ${(nextAllowedAt - now) / 1000} detik…")
            return
        }
        if (recentCount(now) >= MAX_PER_HOUR) {
            setStatus("Batas $MAX_PER_HOUR/jam tercapai. Menunggu…")
            return
        }
        val row = findFirst(root, "Ikuti di Instagram")
        if (row == null) {
            idleTicks++
            setStatus("Tidak ada tugas Follow terlihat")
            if (idleTicks >= 30) {
                idleTicks = 0
                refreshPage()
            }
            return
        }
        idleTicks = 0
        clickNode(row)
        phaseSince = now
        setStatus("Membuka tugas…")
    }

    private fun onTaskDone(now: Long) {
        doneCount++
        doneTimes.add(now)
        resetTask()
        nextAllowedAt = now + Random.nextLong(20000, 60000)
        setStatus("✅ Tugas selesai. Jeda sebelum tugas berikutnya.")
    }

    private fun cancelTask(root: AccessibilityNodeInfo, now: Long) {
        findFirst(root, "Batalkan tugas")?.let { clickNode(it) }
        resetTask()
        nextAllowedAt = now + 30000
        setStatus("Tugas dibatalkan (gagal). Lanjut berikutnya.")
    }

    private fun resetTask() {
        phase = Phase.LIST
        phaseSince = System.currentTimeMillis()
        checkAttempts = 0
        igRetries = 0
        triedDirect = false
        currentUser = null
    }

    private fun recentCount(now: Long): Int {
        doneTimes.removeAll { now - it > 3_600_000L }
        return doneTimes.size
    }

    // ---------------------------------------------------------------- Navigasi

    private fun openInstagramDirect() {
        val user = currentUser ?: return
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$user/"))
                .setPackage(IG_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (_: Exception) {}
    }

    private fun launchBrowser() {
        val pkg = browserPkg ?: return
        try {
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
            }
        } catch (_: Exception) {}
    }

    private fun refreshPage() {
        val pkg = browserPkg ?: return
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://pr.social/tasks?net=instagram"))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- Helper node

    private fun findAll(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> =
        root.findAccessibilityNodeInfosByText(text) ?: emptyList()

    private fun onScreen(n: AccessibilityNodeInfo): Boolean {
        if (n.isVisibleToUser) return true
        val r = Rect()
        n.getBoundsInScreen(r)
        val h = resources.displayMetrics.heightPixels
        return r.width() > 0 && r.height() > 0 && r.top >= 0 && r.bottom <= h
    }

    private fun findFirst(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? =
        findAll(root, text).firstOrNull { onScreen(it) }

    private fun findStartButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findAll(root, "Mulai").firstOrNull { n ->
            val t = n.text?.toString()?.trim() ?: n.contentDescription?.toString()?.trim() ?: ""
            t.startsWith("Mulai", ignoreCase = true) && t.contains("$") && onScreen(n)
        }

    private fun findExact(root: AccessibilityNodeInfo, vararg labels: String): AccessibilityNodeInfo? {
        for (label in labels) {
            val hit = findAll(root, label).firstOrNull { n ->
                onScreen(n) && (
                    n.text?.toString()?.trim().equals(label, ignoreCase = true) ||
                        n.contentDescription?.toString()?.trim().equals(label, ignoreCase = true)
                    )
            }
            if (hit != null) return hit
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable && cur.isEnabled && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            cur = cur.parent
            depth++
        }
        return tapNode(node)
    }

    private fun tapNode(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        private const val IG_PACKAGE = "com.instagram.android"
        private const val MAX_PER_HOUR = 30
    }
}
