package com.ikeralvaro.phonecleaner.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reminderEnabled: Boolean = true,
    val onboardingDone: Boolean = false,
    val lastCleanAt: Long = 0L,
    val totalFreedBytes: Long = 0L,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val REMINDER = booleanPreferencesKey("reminder_enabled")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val LAST_CLEAN = longPreferencesKey("last_clean_at")
        val TOTAL_FREED = longPreferencesKey("total_freed_bytes")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            reminderEnabled = p[Keys.REMINDER] ?: true,
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            lastCleanAt = p[Keys.LAST_CLEAN] ?: 0L,
            totalFreedBytes = p[Keys.TOTAL_FREED] ?: 0L,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setReminderEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.REMINDER] = enabled }

    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.ONBOARDING] = true }

    /** Registra una limpieza real: suma lo liberado y guarda la fecha. */
    suspend fun recordClean(freedBytes: Long, now: Long = System.currentTimeMillis()) {
        if (freedBytes <= 0) return
        context.dataStore.edit {
            it[Keys.LAST_CLEAN] = now
            it[Keys.TOTAL_FREED] = (it[Keys.TOTAL_FREED] ?: 0L) + freedBytes
        }
    }
}
