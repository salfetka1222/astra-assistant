package com.astra.assistant.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

@Composable
internal fun VoiceInputButton(onPhrase: (String) -> Unit, onMessage: (String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val phrase = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { it.isNotBlank() }?.trim()
            if (phrase == null) onMessage("Не удалось разобрать речь. Попробуй ещё раз.")
            else onPhrase(phrase)
        }
    }
    IconButton(onClick = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажи команду или задай вопрос")
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            onMessage("На телефоне нет службы голосового ввода. Можно использовать микрофон клавиатуры.")
        } catch (_: SecurityException) {
            onMessage("Android запретил голосовой ввод. Проверь доступ к микрофону у службы распознавания.")
        }
    }) { Icon(Icons.Filled.Mic, contentDescription = "Голосовой ввод") }
}
