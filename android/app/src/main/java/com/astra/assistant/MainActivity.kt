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
                    primary = Color(0xFFFFB3B5),
                    secondary = Color(0xFFE6BDBD),
                    background = Color(0xFF1B1112),
                    surface = Color(0xFF261B1D),
                    surfaceVariant = Color(0xFF493638),
                    onBackground = Color(0xFFFFEDEC),
                    onSurface = Color(0xFFFFEDEC),
                    onSurfaceVariant = Color(0xFFE4C3C4),
                    primaryContainer = Color(0xFF73363C),
                    onPrimaryContainer = Color(0xFFFFDADB)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFFB65359),
                    secondary = Color(0xFF93666A),
                    background = Color(0xFFFFF8F7),
                    surface = Color(0xFFFFFBFA),
                    surfaceVariant = Color(0xFFF3DDDE),
                    onBackground = Color(0xFF291719),
                    onSurface = Color(0xFF291719),
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
