package com.prautotaskbot.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Model tugas universal untuk semua platform.
 * Digunakan oleh antrean, history, dan adapter platform.
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Platform: Instagram, TikTok, X, dll */
    val platform: String,

    /** Jenis tugas: FOLLOW, LIKE, COMMENT, OTHER */
    val type: TaskType,

    /** Username target (contoh: @username) */
    val targetUsername: String? = null,

    /** URL target lengkap */
    val targetUrl: String? = null,

    /** Reward yang ditampilkan di PR.SOCIAL (belum dikonfirmasi sampai status COMPLETED) */
    val reward: String? = null,

    /** Status pengerjaan */
    val status: TaskStatus = TaskStatus.PENDING,

    /** Waktu tugas ditemukan / dibuat */
    val foundAt: Long = System.currentTimeMillis(),

    /** Waktu mulai dikerjakan */
    val startedAt: Long? = null,

    /** Waktu selesai / gagal */
    val finishedAt: Long? = null,

    /** Catatan tambahan (alasan gagal, dll) */
    val note: String? = null,

    /** ID internal dari PR.SOCIAL jika tersedia */
    val externalId: String? = null
)

enum class TaskType {
    FOLLOW,
    LIKE,
    COMMENT,
    OTHER
}

enum class TaskStatus {
    PENDING,        // Masih di antrean
    IN_PROGRESS,    // Sedang dikerjakan pengguna
    COMPLETED,      // Dikonfirmasi berhasil di PR.SOCIAL
    FAILED,         // Verifikasi gagal
    SKIPPED         // Dilewati pengguna
}
