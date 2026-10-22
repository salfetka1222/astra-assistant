package com.astra.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.astra.assistant.ui.ChatScreen

private const val PREFS_NAME = "astra_preferences"
private const val THEME_KEY = "theme_mode"

enum class ThemeMode(val storageValue: String, val title: String) {
    SYSTEM("system", "Как в системе"),
    LIGHT("light", "Светлая"),
    DARK("dark", "Тёмная");

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            values().firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

class MainActivity : ComponentActivity() {
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeMode = ThemeMode.fromStorage(
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(THEME_KEY, "system")
        )

        setContent {
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            val colors = if (darkTheme) {
                darkColorScheme(
                    primary = Color(0xFFB7B5FF),
                    secondary = Color(0xFFCAC8FF),
                    background = Color(0xFF12121A),
                    surface = Color(0xFF1D1D29),
                    surfaceVariant = Color(0xFF30303D),
                    onBackground = Color(0xFFF1F0FA),
                    onSurface = Color(0xFFF1F0FA),
                    onSurfaceVariant = Color(0xFFD0CEE0),
                    primaryContainer = Color(0xFF34335F),
                    onPrimaryContainer = Color(0xFFE5E3FF)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF5B5BD6),
                    secondary = Color(0xFF7676E8),
                    background = Color(0xFFF7F7FC),
                    surface = Color.White,
                    surfaceVariant = Color(0xFFEAEAFF),
                    onBackground = Color(0xFF191923),
                    onSurface = Color(0xFF191923),
                    onSurfaceVariant = Color(0xFF49485A),
                    primaryContainer = Color(0xFFDEDEFF),
                    onPrimaryContainer = Color(0xFF24234A)
                )
            }

            MaterialTheme(colorScheme = colors) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ChatScreen(
                        themeMode = themeMode,
                        onThemeModeChange = { newMode ->
                            themeMode = newMode
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit()
                                .putString(THEME_KEY, newMode.storageValue)
                                .apply()
                        }
                    )
                }
            }
        }
    }
}
