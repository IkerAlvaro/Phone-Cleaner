package com.ikeralvaro.phonecleaner.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ikeralvaro.phonecleaner.core.ads.AdHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reminderEnabled: Boolean = true,
    val onboardingDone: Boolean = false,
    val lastCleanAt: Long = 0L,
    val totalFreedBytes: Long = 0L,
    /** Compró «Quitar anuncios». */
    val premium: Boolean = false,
    val adHistory: AdHistory = AdHistory(),
)

// Si el archivo se corrompe se empieza de cero en lugar de cerrar la app.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val REMINDER = booleanPreferencesKey("reminder_enabled")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val LAST_CLEAN = longPreferencesKey("last_clean_at")
        val TOTAL_FREED = longPreferencesKey("total_freed_bytes")
        val PREMIUM = booleanPreferencesKey("premium")
        val AD_TASKS = intPreferencesKey("ad_tasks_completed")
        val AD_LAST = longPreferencesKey("ad_last_interstitial_at")
        val AD_DAY = longPreferencesKey("ad_day")
        val AD_TODAY = intPreferencesKey("ad_shown_today")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            reminderEnabled = p[Keys.REMINDER] ?: true,
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            lastCleanAt = p[Keys.LAST_CLEAN] ?: 0L,
            totalFreedBytes = p[Keys.TOTAL_FREED] ?: 0L,
            premium = p[Keys.PREMIUM] ?: false,
            adHistory = AdHistory(
                tasksCompleted = p[Keys.AD_TASKS] ?: 0,
                lastShownAt = p[Keys.AD_LAST] ?: 0L,
                day = p[Keys.AD_DAY] ?: 0L,
                shownToday = p[Keys.AD_TODAY] ?: 0,
            ),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setReminderEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.REMINDER] = enabled }

    suspend fun setPremium(premium: Boolean) = context.dataStore.edit { it[Keys.PREMIUM] = premium }

    suspend fun saveAdHistory(history: AdHistory) = context.dataStore.edit {
        it[Keys.AD_TASKS] = history.tasksCompleted
        it[Keys.AD_LAST] = history.lastShownAt
        it[Keys.AD_DAY] = history.day
        it[Keys.AD_TODAY] = history.shownToday
    }

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
