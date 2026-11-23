package com.prautotaskbot.platform

import com.prautotaskbot.data.model.Task
import com.prautotaskbot.data.model.TaskType

/**
 * Adapter untuk Instagram.
 * Hanya membantu menampilkan info dan membuka target.
 * Pengguna yang melakukan Follow / Like / Comment dengan akun pribadinya.
 */
class InstagramAdapter : PlatformAdapter {

    override val platformId: String = "instagram"
    override val displayName: String = "Instagram"
    override val isReady: Boolean = true

    override fun createTask(
        type: TaskType,
        targetUsername: String?,
        targetUrl: String?,
        reward: String?,
        externalId: String?
    ): Task {
        return Task(
            platform = platformId,
            type = type,
            targetUsername = targetUsername?.removePrefix("@"),
            targetUrl = targetUrl ?: targetUsername?.let { "https://www.instagram.com/$it/" },
            reward = reward,
            externalId = externalId
        )
    }

    override fun getOpenTargetUrl(task: Task): String? {
        return task.targetUrl
            ?: task.targetUsername?.let { "https://www.instagram.com/$it/" }
    }

    override fun getInstruction(task: Task): String {
        return when (task.type) {
            TaskType.FOLLOW -> "Buka profil → tekan Follow → kembali ke PR.SOCIAL untuk verifikasi."
            TaskType.LIKE -> "Buka postingan → tekan Like → kembali ke PR.SOCIAL."
            TaskType.COMMENT -> "Buka postingan → tulis komentar sesuai instruksi → kembali ke PR.SOCIAL."
            TaskType.OTHER -> "Lakukan tindakan sesuai instruksi di PR.SOCIAL."
        }
    }
}
