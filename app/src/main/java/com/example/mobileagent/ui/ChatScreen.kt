package com.example.mobileagent.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val msgs by vm.msgs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) {
            listState.animateScrollToItem(msgs.lastIndex)
        }
    }

    // لانچر برای تشخیص صدا
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                vm.send(spoken)
            }
        }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "بگو چی کارت دارم…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            // اگه اپ تشخیص صدا نبود، کاربر رو به نصب هدایت کن
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://search?q=google+speech"))
                )
            } catch (_: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("دستیار موبایل") })
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(msgs) { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (m.me) Arrangement.Start
                                                else Arrangement.End
                    ) {
                        Surface(
                            color = if (m.me)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.widthIn(max = 300.dp)
                        ) {
                            Text(
                                text = m.text,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("مثلاً: زنگ بزن به علی") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
                Spacer(Modifier.width(6.dp))

                // دکمه‌ی میکروفون
                IconButton(
                    onClick = { startVoice() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "دستور صوتی",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // دکمه‌ی ارسال
                FilledIconButton(
                    onClick = {
                        val t = input.trim()
                        if (t.isNotEmpty()) {
                            vm.send(t)
                            input = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "ارسال")
                }
            }
        }
    }
}
