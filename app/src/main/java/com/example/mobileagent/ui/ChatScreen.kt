package com.example.mobileagent.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileagent.R
import com.example.mobileagent.core.ProgressChannel
import com.example.mobileagent.core.ScanProgress
import com.example.mobileagent.parental.ParentalControlScreen
import com.example.mobileagent.voice.VoiceSettingsScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun nowTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onBackToWelcome: () -> Unit = {}
) {
    val msgs by vm.msgs.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val showThreats by vm.showThreats.collectAsStateWithLifecycle()
    val threats by vm.threats.collectAsStateWithLifecycle()
    val showVoiceSettings by vm.showVoiceSettings.collectAsStateWithLifecycle()
    val voiceStatus by vm.voiceStatus.collectAsStateWithLifecycle()

    // کنترلر والدین — لوکال state
    var showParentalControl by remember { mutableStateOf(false) }

    // صفحه‌ی تهدیدها
    if (showThreats) {
        ThreatDetailScreen(
            threats = threats,
            onBack = { vm.closeThreats() }
        )
        return
    }

    // صفحه‌ی تنظیمات صدا
    if (showVoiceSettings) {
        VoiceSettingsScreen(
            onBack = { vm.closeVoiceSettings() }
        )
        return
    }

    // صفحه‌ی کنترلر والدین
    if (showParentalControl) {
        ParentalControlScreen(
            onBack = { showParentalControl = false }
        )
        return
    }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) {
            listState.animateScrollToItem(msgs.lastIndex)
        }
    }

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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "بگو چی کارت دارم…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Card40()
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "دستیار موبایل",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (busy) "دارم فکر می‌کنم…" else "آنلاین",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToWelcome) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت"
                        )
                    }
                },
                actions = {
                    // 🛡️ کنترلر والدین
                    IconButton(onClick = { showParentalControl = true }) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "کنترلر والدین",
                            tint = cs.primary
                        )
                    }
                    // ⚙️ تنظیمات صدا
                    IconButton(onClick = { vm.openVoiceSettings() }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "تنظیمات صدا",
                            tint = cs.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.surface,
                    titleContentColor = cs.onSurface
                )
            )
        },
        containerColor = cs.background
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(cs.background)
        ) {
            // نوار وضعیت صدا
            voiceStatus?.let { status ->
                Surface(
                    Modifier.fillMaxWidth(),
                    color = cs.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = status,
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // لیست پیام‌ها
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(msgs) { m ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { 20 })
                    ) {
                        MessageBubble(
                            text = m.text,
                            me = m.me,
                            time = nowTime()
                        )
                    }
                }

                if (busy) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                color = cs.surfaceVariant,
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = 18.dp,
                                    bottomEnd = 4.dp
                                )
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = cs.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "دارم فکر می‌کنم…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ScanProgressBar()

            QuickActionsRow(
                enabled = !busy,
                onCommand = { vm.send(it) }
            )

            InputBar(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    val t = input.trim()
                    if (t.isNotEmpty()) {
                        vm.send(t)
                        input = ""
                    }
                },
                onVoice = { startVoice() },
                enabled = !busy
            )
        }
    }
}

@Composable
private fun Card40() {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher),
            contentDescription = "دستیار",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
        )
    }
}

@Composable
private fun MessageBubble(
    text: String,
    me: Boolean,
    time: String
) {
    val cs = MaterialTheme.colorScheme

    if (me) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Surface(
                    color = cs.primary,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 18.dp
                    ),
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = cs.onPrimary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = cs.primaryContainer
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Surface(
                    color = cs.surfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 4.dp
                    ),
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    enabled: Boolean
) {
    val cs = MaterialTheme.colorScheme

    Surface(
        color = cs.surface,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "هر جوری دوست داری بگو…",
                        color = cs.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                enabled = enabled,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.4f),
                    focusedBorderColor = cs.primary,
                    unfocusedBorderColor = Color.Transparent,
                    disabledContainerColor = cs.surfaceVariant.copy(alpha = 0.2f)
                )
            )

            Spacer(Modifier.width(6.dp))

            Surface(
                color = cs.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(
                    onClick = onVoice,
                    enabled = enabled
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "دستور صوتی",
                        tint = cs.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Surface(
                color = if (value.isBlank() || !enabled) cs.surfaceVariant else cs.primary,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank() && enabled
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "ارسال",
                        tint = if (value.isBlank() || !enabled)
                            cs.onSurfaceVariant.copy(alpha = 0.4f)
                        else cs.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionsRow(
    enabled: Boolean,
    onCommand: (String) -> Unit
) {
    val actions = listOf(
        "🛡️" to "ویروس‌ها رو پیدا کن",
        "🧹" to "فایل‌های اضافی رو پیدا کن",
        "📑" to "فایل‌های تکراری رو پیدا کن",
        "💾" to "کش رو پاک کن",
        "📊" to "چقدر فضا اشغال شده",
        "⏰" to "ساعت ۷ آلارم بذار",
        "🚀" to "اینستاگرام رو باز کن"
    )
    val cs = MaterialTheme.colorScheme

    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(actions) { (icon, cmd) ->
            AssistChip(
                onClick = { if (enabled) onCommand(cmd) },
                label = {
                    Text(
                        "$icon ${cmd.take(18)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = cs.secondaryContainer.copy(alpha = 0.6f),
                    labelColor = cs.onSecondaryContainer
                ),
                enabled = enabled
            )
        }
    }
}

@Composable
fun ScanProgressBar() {
    val progress by ProgressChannel.progress.collectAsStateWithLifecycle(
        initialValue = null
    )
    val p = progress
    if (p is ScanProgress.Scanning) {
        val frac = if (p.total > 0) p.current.toFloat() / p.total else 0f
        val cs = MaterialTheme.colorScheme
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = cs.primary,
                trackColor = cs.surfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${p.label}  (${p.current}/${p.total})",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
