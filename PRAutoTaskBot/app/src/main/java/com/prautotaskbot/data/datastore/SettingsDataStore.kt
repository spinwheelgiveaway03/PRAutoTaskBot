package com.prautotaskbot.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val ACTIVE_PLATFORM = stringPreferencesKey("active_platform")
        private val MONITORING_INTERVAL_SEC = intPreferencesKey("monitoring_interval_sec")
        private val PRIORITY_FOLLOW = intPreferencesKey("priority_follow")
        private val PRIORITY_LIKE = intPreferencesKey("priority_like")
        private val PRIORITY_COMMENT = intPreferencesKey("priority_comment")
        private val PRIORITY_OTHER = intPreferencesKey("priority_other")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        private val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val activePlatform: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_PLATFORM] ?: "instagram"
    }

    val monitoringIntervalSec: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[MONITORING_INTERVAL_SEC] ?: 90   // default 60-120 detik
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NOTIFICATIONS_ENABLED] ?: true
    }

    val bubbleEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BUBBLE_ENABLED] ?: false
    }

    val darkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_THEME] ?: true
    }

    // Prioritas (angka lebih kecil = lebih tinggi prioritas)
    val priorityFollow: Flow<Int> = context.dataStore.data.map { it[PRIORITY_FOLLOW] ?: 1 }
    val priorityLike: Flow<Int> = context.dataStore.data.map { it[PRIORITY_LIKE] ?: 2 }
    val priorityComment: Flow<Int> = context.dataStore.data.map { it[PRIORITY_COMMENT] ?: 3 }
    val priorityOther: Flow<Int> = context.dataStore.data.map { it[PRIORITY_OTHER] ?: 4 }

    suspend fun setActivePlatform(platformId: String) {
        context.dataStore.edit { it[ACTIVE_PLATFORM] = platformId }
    }

    suspend fun setMonitoringInterval(seconds: Int) {
        context.dataStore.edit { it[MONITORING_INTERVAL_SEC] = seconds.coerceIn(30, 600) }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BUBBLE_ENABLED] = enabled }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[DARK_THEME] = enabled }
    }

    suspend fun setPriorities(follow: Int, like: Int, comment: Int, other: Int) {
        context.dataStore.edit {
            it[PRIORITY_FOLLOW] = follow
            it[PRIORITY_LIKE] = like
            it[PRIORITY_COMMENT] = comment
            it[PRIORITY_OTHER] = other
        }
    }
}
