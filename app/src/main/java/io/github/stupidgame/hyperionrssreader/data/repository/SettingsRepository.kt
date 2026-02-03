package io.github.stupidgame.hyperionrssreader.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.TimeZone

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val _currentTheme = MutableStateFlow(getThemeFromPrefs())
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()
    
    private val _currentTimeZoneId = MutableStateFlow(getTimeZoneFromPrefs())
    val currentTimeZoneId: StateFlow<String> = _currentTimeZoneId.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("theme", theme.name).apply()
        _currentTheme.value = theme
    }

    private fun getThemeFromPrefs(): AppTheme {
        val themeName = prefs.getString("theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        return try {
            AppTheme.valueOf(themeName)
        } catch (e: IllegalArgumentException) {
            AppTheme.SYSTEM
        }
    }
    
    fun setTimeZone(timeZoneId: String) {
        prefs.edit().putString("time_zone", timeZoneId).apply()
        _currentTimeZoneId.value = timeZoneId
    }

    private fun getTimeZoneFromPrefs(): String {
        return prefs.getString("time_zone", TimeZone.getDefault().id) ?: TimeZone.getDefault().id
    }
}