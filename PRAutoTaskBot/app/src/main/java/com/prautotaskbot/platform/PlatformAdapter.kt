package com.prautotaskbot.platform

import com.prautotaskbot.data.model.Task
import com.prautotaskbot.data.model.TaskType

/**
 * Kontrak modular untuk setiap platform.
 * Implementasi konkret (InstagramAdapter, dll) hanya menangani detail platform-spesifik.
 * Tindakan di media sosial SELALU dilakukan oleh pengguna secara manual.
 */
interface PlatformAdapter {

    val platformId: String
    val displayName: String

    /** Apakah adapter ini sudah siap dipakai */
    val isReady: Boolean

    /**
     * Membuat Task lokal dari data yang diberikan pengguna
     * (URL target, username, jenis, reward).
     */
    fun createTask(
        type: TaskType,
        targetUsername: String?,
        targetUrl: String?,
        reward: String? = null,
        externalId: String? = null
    ): Task

    /**
     * Membuka target di browser / aplikasi resmi platform.
     * Mengembalikan Intent atau URL yang bisa dibuka.
     */
    fun getOpenTargetUrl(task: Task): String?

    /**
     * Instruksi singkat yang ditampilkan ke pengguna.
     */
    fun getInstruction(task: Task): String
}
