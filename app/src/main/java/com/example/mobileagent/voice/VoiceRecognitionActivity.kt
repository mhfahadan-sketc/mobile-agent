package com.example.mobileagent.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Activity شفاف که فقط برای گرفتن دستور صوتی باز می‌شه.
 *
 * جریان:
 *  ۱. FloatingButtonService این Activity رو باز می‌کنه
 *  ۲. این Activity میکروفون گوگل رو باز می‌کنه
 *  ۳. کاربر حرف می‌زنه
 *  ۴. نتیجه به VoiceCommandBus فرستاده می‌شه
 *  ۵. Activity خودش رو می‌بنده
 */
class VoiceRecognitionActivity : ComponentActivity() {

    companion object {
        private const val TAG = "VoiceRecAct"
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
                .trim()

            if (spoken.isNotEmpty()) {
                Log.d(TAG, "Recognized: $spoken")
                VoiceCommandBus.emit(
                    VoiceCommandBus.Event.CommandRecognized(spoken)
                )
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // شفاف و بدون انیمیشن
        overridePendingTransition(0, 0)

        setContent {
            MaterialTheme {
                VoiceOverlayScreen(
                    onCancel = { finish() },
                    onStartListening = { launchSpeech() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // خودکار میکروفون رو باز کن (با یه تأخیر کوچیک که UI نشون داده شه)
        window.decorView.postDelayed({
            launchSpeech()
        }, 300)
    }

    private fun launchSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "بگو چی کارت دارم…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "launchSpeech failed", e)
            finish()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

@Composable
private fun VoiceOverlayScreen(
    onCancel: () -> Unit,
    onStartListening: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(180.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "دارم گوش می‌دم…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.7f),
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .size(20.dp)
                    )
                }
            }
        }

        // دکمه‌ی لغو بالا
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "لغو",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
