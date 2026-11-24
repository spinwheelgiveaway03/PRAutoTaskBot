package com.prautotaskbot.data.model

/**
 * Daftar platform yang didukung berdasarkan PR.SOCIAL.
 * isImplemented = false berarti masih terkunci (fondasi ada, implementasi penuh nanti).
 */
data class PlatformInfo(
    val id: String,
    val displayName: String,
    val iconResName: String,          // nama drawable
    val isImplemented: Boolean = false,
    val priorityOrder: Int = 99
)

object Platforms {
    val ALL = listOf(
        PlatformInfo("instagram", "Instagram", "ic_instagram", isImplemented = true, priorityOrder = 1),
        PlatformInfo("max", "MAX", "ic_max", isImplemented = false),
        PlatformInfo("github", "GitHub", "ic_github", isImplemented = false),
        PlatformInfo("tiktok", "TikTok", "ic_tiktok", isImplemented = false),
        PlatformInfo("linkedin", "LinkedIn", "ic_linkedin", isImplemented = false),
        PlatformInfo("bilibili", "Bilibili", "ic_bilibili", isImplemented = false),
        PlatformInfo("bluesky", "Bluesky", "ic_bluesky", isImplemented = false),
        PlatformInfo("dzen", "Dzen", "ic_dzen", isImplemented = false),
        PlatformInfo("discord", "Discord", "ic_discord", isImplemented = false),
        PlatformInfo("threads", "Threads", "ic_threads", isImplemented = false),
        PlatformInfo("vk", "VK", "ic_vk", isImplemented = false),
        PlatformInfo("kick", "Kick", "ic_kick", isImplemented = false),
        PlatformInfo("snapchat", "Snapchat", "ic_snapchat", isImplemented = false),
        PlatformInfo("telegram", "Telegram", "ic_telegram", isImplemented = false),
        PlatformInfo("twitch", "Twitch", "ic_twitch", isImplemented = false),
        PlatformInfo("ok", "OK", "ic_ok", isImplemented = false),
        PlatformInfo("lemon8", "Lemon8", "ic_lemon8", isImplemented = false),
        PlatformInfo("soundcloud", "SoundCloud", "ic_soundcloud", isImplemented = false),
        PlatformInfo("x", "X (Twitter)", "ic_x", isImplemented = false),
        // Kategori "PLATFORM LAINNYA"
        PlatformInfo("other", "Platform Lainnya (+18)", "ic_other", isImplemented = false, priorityOrder = 100)
    )

    fun getById(id: String): PlatformInfo? = ALL.find { it.id == id }
    fun getImplemented(): List<PlatformInfo> = ALL.filter { it.isImplemented }
}
