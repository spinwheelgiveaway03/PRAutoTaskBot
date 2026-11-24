package com.prautotaskbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.random.Random

/**
 * Bot otomatis tugas "Ikuti di Instagram" di PR.SOCIAL.
 * Cara kerja: potret layar -> baca tulisan (OCR, di HP, tanpa internet) -> ketuk posisi tulisan.
 * Tidak bergantung pada browser mana pun.
 */
class AutomationService : AccessibilityService() {

    private enum class Phase { LIST, IN_INSTAGRAM, IG_CLICKED, RETURNING, CHECKING }

    private class OcrWord(text: String, val rect: Rect) {
        val norm: String = text.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private class OcrLine(val text: String, val rect: Rect, val words: List<OcrWord>) {
        val norm: String = text.lowercase().replace(Regex("\\s+"), " ").trim()
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var loopJob: Job? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private var windowManager: WindowManager? = null
    private var overlay: LinearLayout? = null
    private var toggleView: TextView? = null
    private var statusView: TextView? = null

    private var enabled = false
    private var phase = Phase.LIST
    private var phaseSince = 0L
    private var currentPkg: String? = null
    private var browserPkg: String? = null
    private var checkAttempts = 0
    private var igRetries = 0
    private var igAttempts = 0
    private var verifiedFollow = false
    private var cancelAfterReturn = false
    private var idleTicks = 0
    private var lastRefreshAt = 0L
    private var nextAllowedAt = 0L
    private var doneCount = 0
    private var lastShotError = 0
    private val doneTimes = ArrayList<Long>()
    private var statusText = "Siap. Tekan tombol untuk mulai."

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        startLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val p = event?.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            p != packageName && p != "com.android.systemui"
        ) {
            currentPkg = p
        }
    }

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
            y = 120
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
            setStatus("Bot jalan. Buka halaman Tugas.")
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
                delay(Random.nextLong(1800, 3200))
                if (!enabled) continue
                try {
                    tick()
                } catch (e: Exception) {
                    setStatus("Error: ${e.message}")
                }
            }
        }
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val pkg = rootInActiveWindow?.packageName?.toString() ?: currentPkg
        if (pkg == packageName) return

        val bmp = captureBitmap()
        if (bmp == null) {
            setStatus("Gagal potret layar (kode $lastShotError)")
            return
        }
        val all = ocr(bmp)
        bmp.recycle()
        val ov = overlayRect()
        val lines = all.filter { !ov.contains(it.rect.centerX(), it.rect.centerY()) }

        if (pkg == IG_PACKAGE) {
            handleInstagram(lines, now)
            return
        }
        val looksPr = lines.any {
            it.norm.contains("ikuti di instagram") || it.norm.contains("periksa follow saya") ||
                it.norm.contains("buka profil dan ikuti") || it.norm.contains("tugas saya")
        }
        if (looksPr) {
            if (pkg != null) browserPkg = pkg
            handlePr(lines, now)
        } else {
            setStatus("Halaman Tugas tidak terlihat (OCR ${lines.size} baris)")
        }
    }

    // ---------------------------------------------------------------- Screenshot + OCR

    private suspend fun captureBitmap(): Bitmap? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < 30) {
            lastShotError = -1
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        try {
                            val hb = result.hardwareBuffer
                            val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                            val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            hb.close()
                            cont.resume(soft)
                        } catch (e: Exception) {
                            lastShotError = -2
                            cont.resume(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        lastShotError = errorCode
                        cont.resume(null)
                    }
                }
            )
        } catch (e: Exception) {
            lastShotError = -3
            cont.resume(null)
        }
    }

    private suspend fun ocr(bmp: Bitmap): List<OcrLine> = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { t ->
                val out = ArrayList<OcrLine>()
                for (b in t.textBlocks) {
                    for (l in b.lines) {
                        val r = l.boundingBox ?: continue
                        val ws = ArrayList<OcrWord>()
                        for (e in l.elements) {
                            val er = e.boundingBox ?: continue
                            ws.add(OcrWord(e.text, er))
                        }
                        out.add(OcrLine(l.text, r, ws))
                    }
                }
                cont.resume(out)
            }
            .addOnFailureListener { cont.resume(emptyList()) }
    }

    private fun overlayRect(): Rect {
        val o = overlay ?: return Rect()
        val loc = IntArray(2)
        o.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + o.width, loc[1] + o.height)
    }

    // ---------------------------------------------------------------- Instagram

    private fun handleInstagram(lines: List<OcrLine>, now: Long) {
        if (lines.any {
                it.norm.contains("coba lagi nanti") || it.norm.contains("try again later") ||
                    it.norm.contains("tindakan diblokir") || it.norm.contains("action blocked")
            }
        ) {
            enabled = false
            setStatus("⛔ Instagram membatasi akun. Bot dimatikan, istirahat dulu.")
            Toast.makeText(this, "Instagram membatasi aksi. Bot dimatikan.", Toast.LENGTH_LONG).show()
            return
        }

        val h = resources.displayMetrics.heightPixels
        // Hanya baris yang isinya kata-kata tombol (Ikuti, Kirim Pesan, Berlangganan, dst).
        val strict = lines.filter { l ->
            l.words.isNotEmpty() && l.words.all { it.norm in BUTTON_WORDS || it.norm.length <= 2 }
        }
        val cands = strict.flatMap { it.words }.filter {
            it.rect.centerY() > h * 0.26 && it.rect.centerY() < h * 0.65
        }
        val following = cands.firstOrNull { it.norm in FOLLOWING_WORDS }
        val followBtn = cands.firstOrNull { it.norm in FOLLOW_WORDS }

        when (phase) {
            Phase.IN_INSTAGRAM -> {
                if (following != null) {
                    verifiedFollow = true
                    returnToBrowser(now, "Sudah mengikuti")
                } else if (followBtn != null) {
                    tap(followBtn.rect)
                    phase = Phase.IG_CLICKED
                    phaseSince = now
                    igRetries = 0
                    setStatus("Menekan tombol Ikuti…")
                } else if (now - phaseSince > PROFILE_TIMEOUT) {
                    retryProfile(now, "Profil lama / tombol Ikuti tidak ketemu")
                } else {
                    setStatus("Mencari tombol Ikuti… (percobaan ${igAttempts + 1}/$MAX_PROFILE_TRIES)")
                }
            }
            Phase.IG_CLICKED -> {
                if (following != null) {
                    verifiedFollow = true
                    returnToBrowser(now, "Berhasil mengikuti")
                } else if (now - phaseSince > 6000) {
                    if (followBtn != null && igRetries < 2) {
                        igRetries++
                        tap(followBtn.rect)
                        phaseSince = now
                    } else {
                        retryProfile(now, "Ikuti belum berhasil")
                    }
                }
            }
            Phase.RETURNING -> {
                if (now - phaseSince > 4000) {
                    launchBrowser()
                    phaseSince = now
                }
            }
            else -> {}
        }
    }

    private fun returnToBrowser(now: Long, msg: String) {
        if (browserPkg != null) launchBrowser() else performGlobalAction(GLOBAL_ACTION_BACK)
        phase = Phase.RETURNING
        phaseSince = now
        setStatus("$msg, kembali ke halaman tugas")
    }

    /** Gagal menemukan/menekan Ikuti: kembali ke web lalu buka profil lagi (bukan menekan Periksa). */
    private fun retryProfile(now: Long, msg: String) {
        igAttempts++
        verifiedFollow = false
        cancelAfterReturn = igAttempts >= MAX_PROFILE_TRIES
        if (browserPkg != null) launchBrowser() else performGlobalAction(GLOBAL_ACTION_BACK)
        phase = Phase.RETURNING
        phaseSince = now
        setStatus("$msg. Ulangi buka profil ($igAttempts/$MAX_PROFILE_TRIES)")
    }

    // ---------------------------------------------------------------- Halaman tugas

    private fun handlePr(lines: List<OcrLine>, now: Long) {
        val check = lines.firstOrNull { it.norm.contains("periksa follow saya") }
        val reviewed = lines.any { it.norm.contains("ditinjau") }
        val startBtn = lines.firstOrNull {
            it.norm.startsWith("mulai") && !it.norm.contains("dalam") && it.norm.any { c -> c.isDigit() }
        }
        val confirmMark = lines.any { it.norm.contains("konfirmasi follow anda") }
        val modalOpen = check != null || startBtn != null || confirmMark || reviewed

        if (phase == Phase.CHECKING && (check == null || reviewed)) {
            onTaskDone(now)
            return
        }

        if (check == null && startBtn != null) {
            if (!canStartNew(now)) return
            tap(startBtn.rect)
            phase = Phase.LIST
            phaseSince = now
            setStatus("Menekan Mulai…")
            return
        }

        val nextBtn = lines.firstOrNull { it.norm.contains("tugas berikutnya") }
        if (check == null && nextBtn != null) {
            if (!canStartNew(now)) return
            tap(nextBtn.rect)
            phase = Phase.LIST
            phaseSince = now
            setStatus("Lanjut ke tugas berikutnya…")
            return
        }

        if (!modalOpen) {
            if (phase == Phase.LIST) {
                listStep(lines, now)
            } else if (now - phaseSince > 15000) {
                resetTask()
            } else {
                setStatus("Menunggu halaman tugas…")
            }
            return
        }

        if (check == null) {
            // modal terbuka tapi belum jelas, tunggu
            setStatus("Menunggu jendela tugas…")
            return
        }

        when (phase) {
            Phase.LIST -> {
                if (!canStartNew(now)) return
                tapLink(lines)
                phase = Phase.IN_INSTAGRAM
                phaseSince = now
                checkAttempts = 0
                igRetries = 0
                igAttempts = 0
                verifiedFollow = false
                cancelAfterReturn = false
                setStatus("Membuka profil Instagram…")
            }
            Phase.IN_INSTAGRAM -> {
                // Masih di halaman tugas: link belum membuka Instagram
                if (now - phaseSince > 12000) {
                    igAttempts++
                    if (igAttempts >= MAX_PROFILE_TRIES) {
                        cancelTask(lines, now)
                    } else {
                        tapLink(lines)
                        phaseSince = now
                        setStatus("Link belum terbuka, coba lagi ($igAttempts/$MAX_PROFILE_TRIES)")
                    }
                }
            }
            Phase.IG_CLICKED, Phase.RETURNING -> {
                if (cancelAfterReturn) {
                    cancelTask(lines, now)
                } else if (!verifiedFollow) {
                    tapLink(lines)
                    phase = Phase.IN_INSTAGRAM
                    phaseSince = now
                    setStatus("Membuka ulang profil Instagram…")
                } else {
                    tap(check.rect)
                    phase = Phase.CHECKING
                    phaseSince = now
                    checkAttempts = 1
                    setStatus("Follow terverifikasi, menekan periksa…")
                }
            }
            Phase.CHECKING -> {
                if (now - phaseSince > 9000) {
                    if (checkAttempts >= 3) {
                        cancelTask(lines, now)
                    } else {
                        tap(check.rect)
                        checkAttempts++
                        phaseSince = now
                        setStatus("Coba periksa lagi ($checkAttempts/3)")
                    }
                }
            }
        }
    }

    private fun listStep(lines: List<OcrLine>, now: Long) {
        if (!canStartNew(now)) return
        val h = resources.displayMetrics.heightPixels
        val row = lines
            .filter {
                it.norm.contains("ikuti di instagram") &&
                    it.rect.centerY() > h * 0.18 && it.rect.centerY() < h * 0.85
            }
            .minByOrNull { it.rect.top }
        if (row == null) {
            idleTicks++
            setStatus("Tidak ada tugas Follow terlihat (OCR ${lines.size} baris)")
            if (idleTicks >= 4 && now - lastRefreshAt > 20000) {
                idleTicks = 0
                lastRefreshAt = now
                setStatus("Tugas habis, memuat ulang halaman…")
                refreshPage()
            }
            return
        }
        idleTicks = 0
        tap(row.rect)
        phaseSince = now
        setStatus("Membuka tugas…")
    }

    private fun tapLink(lines: List<OcrLine>) {
        val link = lines.firstOrNull { it.norm.contains("instagram.com") }
            ?: lines.firstOrNull { it.norm.contains("buka profil dan ikuti") }
        if (link != null) tap(link.rect)
    }

    private fun canStartNew(now: Long): Boolean {
        if (now < nextAllowedAt) {
            setStatus("Jeda ${(nextAllowedAt - now) / 1000} detik…")
            return false
        }
        if (recentCount(now) >= MAX_PER_HOUR) {
            setStatus("Batas $MAX_PER_HOUR per jam tercapai. Menunggu…")
            return false
        }
        return true
    }

    private fun onTaskDone(now: Long) {
        doneCount++
        doneTimes.add(now)
        resetTask()
        nextAllowedAt = now + Random.nextLong(5000, 10000)
        setStatus("✅ Tugas selesai. Jeda sebelum tugas berikutnya.")
    }

    private fun cancelTask(lines: List<OcrLine>, now: Long) {
        lines.firstOrNull { it.norm.contains("batalkan tugas") }?.let { tap(it.rect) }
        resetTask()
        nextAllowedAt = now + 8000
        setStatus("Tugas dibatalkan (gagal). Lanjut berikutnya.")
    }

    private fun resetTask() {
        phase = Phase.LIST
        phaseSince = System.currentTimeMillis()
        checkAttempts = 0
        igRetries = 0
        igAttempts = 0
        verifiedFollow = false
        cancelAfterReturn = false
    }

    private fun recentCount(now: Long): Int {
        doneTimes.removeAll { now - it > 3_600_000L }
        return doneTimes.size
    }

    // ---------------------------------------------------------------- Aksi

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

    private fun tap(r: Rect): Boolean {
        if (r.isEmpty) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 90))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        private const val IG_PACKAGE = "com.instagram.android"
        private const val MAX_PER_HOUR = 50
        private const val MAX_PROFILE_TRIES = 5
        private const val PROFILE_TIMEOUT = 35_000L
        private val FOLLOW_WORDS = setOf("ikuti", "follow")
        private val FOLLOWING_WORDS = setOf("mengikuti", "following", "diminta", "requested")
        private val BUTTON_WORDS = setOf(
            "ikuti", "follow", "balik", "back", "kirim", "pesan", "message",
            "berlangganan", "subscribe", "mengikuti", "following", "diminta", "requested"
        )
    }
}
