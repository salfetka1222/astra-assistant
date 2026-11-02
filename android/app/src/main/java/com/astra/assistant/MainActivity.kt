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

            // Every Material 3 surface and container follows the selected palette.
            // Red remains the default visual identity; other palettes are fully themed.
            val colors = when (accent) {
                "blue" -> if (darkTheme) darkColorScheme(
                    primary = Color(0xFFACC7FF), onPrimary = Color(0xFF102E60),
                    secondary = Color(0xFFB5C7E9), onSecondary = Color(0xFF1A3055),
                    background = Color(0xFF101723), onBackground = Color(0xFFE5ECFF),
                    surface = Color(0xFF192233), onSurface = Color(0xFFE5ECFF),
                    surfaceVariant = Color(0xFF303E57), onSurfaceVariant = Color(0xFFC6D4EF),
                    primaryContainer = Color(0xFF294778), onPrimaryContainer = Color(0xFFD8E5FF),
                    secondaryContainer = Color(0xFF304461), onSecondaryContainer = Color(0xFFDFE9FF),
                    surfaceTint = Color(0xFFACC7FF)
                ) else lightColorScheme(
                    primary = Color(0xFF4465A5), onPrimary = Color.White,
                    secondary = Color(0xFF536B99), onSecondary = Color.White,
                    background = Color(0xFFF6F8FF), onBackground = Color(0xFF14233E),
                    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF14233E),
                    surfaceVariant = Color(0xFFDDE7FA), onSurfaceVariant = Color(0xFF435675),
                    primaryContainer = Color(0xFFD8E5FF), onPrimaryContainer = Color(0xFF183D7C),
                    secondaryContainer = Color(0xFFDDE6F9), onSecondaryContainer = Color(0xFF314C7C),
                    surfaceTint = Color(0xFF4465A5)
                )
                "green" -> if (darkTheme) darkColorScheme(
                    primary = Color(0xFFA8D7BC), onPrimary = Color(0xFF143D2A),
                    secondary = Color(0xFFB8D3C3), onSecondary = Color(0xFF203D2F),
                    background = Color(0xFF101B17), onBackground = Color(0xFFE2F3E9),
                    surface = Color(0xFF1B2A23), onSurface = Color(0xFFE2F3E9),
                    surfaceVariant = Color(0xFF30493B), onSurfaceVariant = Color(0xFFC4E2CF),
                    primaryContainer = Color(0xFF285C43), onPrimaryContainer = Color(0xFFD2F5DD),
                    secondaryContainer = Color(0xFF345443), onSecondaryContainer = Color(0xFFD7EEDF),
                    surfaceTint = Color(0xFFA8D7BC)
                ) else lightColorScheme(
                    primary = Color(0xFF387957), onPrimary = Color.White,
                    secondary = Color(0xFF4D765C), onSecondary = Color.White,
                    background = Color(0xFFF5FBF5), onBackground = Color(0xFF183324),
                    surface = Color.White, onSurface = Color(0xFF183324),
                    surfaceVariant = Color(0xFFDDEEE2), onSurfaceVariant = Color(0xFF496652),
                    primaryContainer = Color(0xFFD0EED9), onPrimaryContainer = Color(0xFF1B5133),
                    secondaryContainer = Color(0xFFDCEDE0), onSecondaryContainer = Color(0xFF2F5C3E),
                    surfaceTint = Color(0xFF387957)
                )
                "purple" -> if (darkTheme) darkColorScheme(
                    primary = Color(0xFFD5B9F5), onPrimary = Color(0xFF432360),
                    secondary = Color(0xFFD0BCE5), onSecondary = Color(0xFF412C58),
                    background = Color(0xFF1C1424), onBackground = Color(0xFFF1E5FA),
                    surface = Color(0xFF291D33), onSurface = Color(0xFFF1E5FA),
                    surfaceVariant = Color(0xFF493554), onSurfaceVariant = Color(0xFFE1C9EE),
                    primaryContainer = Color(0xFF603B80), onPrimaryContainer = Color(0xFFF0D8FF),
                    secondaryContainer = Color(0xFF523A65), onSecondaryContainer = Color(0xFFEAD8F5),
                    surfaceTint = Color(0xFFD5B9F5)
                ) else lightColorScheme(
                    primary = Color(0xFF8158A3), onPrimary = Color.White,
                    secondary = Color(0xFF786087), onSecondary = Color.White,
                    background = Color(0xFFFCF7FF), onBackground = Color(0xFF2D2035),
                    surface = Color.White, onSurface = Color(0xFF2D2035),
                    surfaceVariant = Color(0xFFEEDFF5), onSurfaceVariant = Color(0xFF65516F),
                    primaryContainer = Color(0xFFF0D9FF), onPrimaryContainer = Color(0xFF593378),
                    secondaryContainer = Color(0xFFEEDFF3), onSecondaryContainer = Color(0xFF5B446A),
                    surfaceTint = Color(0xFF8158A3)
                )
                else -> if (darkTheme) darkColorScheme(
                    primary = Color(0xFFFFB3B5), onPrimary = Color(0xFF632B30),
                    secondary = Color(0xFFE6BDBD), onSecondary = Color(0xFF4E292B),
                    background = Color(0xFF1B1112), surface = Color(0xFF261B1D),
                    surfaceVariant = Color(0xFF493638), onBackground = Color(0xFFFFEDEC),
                    onSurface = Color(0xFFFFEDEC), onSurfaceVariant = Color(0xFFE4C3C4),
                    primaryContainer = Color(0xFF73363C), onPrimaryContainer = Color(0xFFFFDADB),
                    secondaryContainer = Color(0xFF5B3B3E), onSecondaryContainer = Color(0xFFFFDADB)
                ) else lightColorScheme(
                    primary = Color(0xFFB65359), onPrimary = Color.White,
                    secondary = Color(0xFF93666A), onSecondary = Color.White,
                    background = Color(0xFFFFF8F7), surface = Color(0xFFFFFBFA),
                    surfaceVariant = Color(0xFFF3DDDE), onBackground = Color(0xFF291719),
                    onSurface = Color(0xFF291719), onSurfaceVariant = Color(0xFF655052),
                    primaryContainer = Color(0xFFFFDADB), onPrimaryContainer = Color(0xFF702E35),
                    secondaryContainer = Color(0xFFF3DDDE), onSecondaryContainer = Color(0xFF702E35)
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
