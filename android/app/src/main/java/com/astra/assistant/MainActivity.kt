package com.astra.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.astra.assistant.ui.ChatScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = androidx.compose.material3.lightColorScheme(
                    primary = Color(0xFF5B5BD6),
                    secondary = Color(0xFF7676E8),
                    background = Color(0xFFF7F7FC),
                    surface = Color.White
                )
            ) {
                Surface {
                    ChatScreen()
                }
            }
        }
    }
}
