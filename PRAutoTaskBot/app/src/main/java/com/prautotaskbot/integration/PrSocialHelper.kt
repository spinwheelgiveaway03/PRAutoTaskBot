package com.prautotaskbot.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Helper integrasi dengan PR.SOCIAL.
 *
 * Karena PR.SOCIAL adalah situs web (bukan API publik yang stabil untuk task feed tanpa autentikasi),
 * pendekatan yang aman dan sesuai batasan:
 * 1. Membuka https://pr.social di browser / Custom Tab.
 * 2. Pengguna login & mengambil tugas secara normal di situs.
 * 3. Pengguna memasukkan info tugas (URL target / username / jenis) ke aplikasi
 *    atau menyalin link target, lalu aplikasi membantu membuka target & mencatat status.
 *
 * Tidak melakukan scraping otomatis HTML (rapuh & berisiko melanggar ToS).
 * Tidak menyimpan kredensial PR.SOCIAL maupun media sosial.
 */
object PrSocialHelper {

    const val BASE_URL = "https://pr.social"
    const val TASKS_URL = "https://pr.social" // halaman tugas utama

    fun openPrSocial(context: Context) {
        openUrl(context, BASE_URL)
    }

    fun openTasksPage(context: Context) {
        openUrl(context, TASKS_URL)
    }

    fun openUrl(context: Context, url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            // Fallback ke browser biasa
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Tidak bisa membuka browser: ${e2.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Coba deteksi jenis tugas dari teks sederhana (untuk input manual / clipboard).
     */
    fun detectTaskType(text: String): com.prautotaskbot.data.model.TaskType {
        val lower = text.lowercase()
        return when {
            lower.contains("follow") || lower.contains("ikuti") || lower.contains("subscribe") ->
                com.prautotaskbot.data.model.TaskType.FOLLOW
            lower.contains("like") || lower.contains("suka") || lower.contains("❤") ->
                com.prautotaskbot.data.model.TaskType.LIKE
            lower.contains("comment") || lower.contains("komentar") ->
                com.prautotaskbot.data.model.TaskType.COMMENT
            else -> com.prautotaskbot.data.model.TaskType.OTHER
        }
    }
}
