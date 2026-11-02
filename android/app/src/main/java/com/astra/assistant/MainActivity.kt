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
private const val ACCENT_KEY = "accent_color"
private const val BUTTON_KEY = "button_size"

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
    private var accent by mutableStateOf("red")
    private var buttonSize by mutableStateOf("normal")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeMode = ThemeMode.fromStorage(
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(THEME_KEY, "system")
        )

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        accent = prefs.getString(ACCENT_KEY, "red") ?: "red"
        buttonSize = prefs.getString(BUTTON_KEY, "normal") ?: "normal"
        setContent {
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            val primary = when (accent) {
                "blue" -> if (darkTheme) Color(0xFFACC7FF) else Color(0xFF4465A5)
                "green" -> if (darkTheme) Color(0xFFA8D7BC) else Color(0xFF387957)
                "purple" -> if (darkTheme) Color(0xFFD5B9F5) else Color(0xFF8158A3)
                else -> if (darkTheme) Color(0xFFFFB3B5) else Color(0xFFB65359)
            }
            val colors = if (darkTheme) {
                darkColorScheme(
                    primary = primary,
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
                    primary = primary,
                    secondary = Color(0xFF93666A),
                    background = Color(0xFFFFF8F7),
                    surface = Color(0xFFFFFBFA),
                    surfaceVariant = Color(0xFFF3DDDE),
                    onBackground = Color(0xFF291719),
                    onSurface = Color(0xFF291719),
                    onSurfaceVariant = Color(0xFF655052),
                    primaryContainer = Color(0xFFFFDADB),
                    onPrimaryContainer = Color(0xFF702E35)
                )
            }

            MaterialTheme(colorScheme = colors) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ChatScreen(
                        themeMode = themeMode,
                        accent = accent, onAccentChange = { accent = it; prefs.edit().putString(ACCENT_KEY, it).apply() },
                        buttonSize = buttonSize, onButtonSizeChange = { buttonSize = it; prefs.edit().putString(BUTTON_KEY, it).apply() },
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
