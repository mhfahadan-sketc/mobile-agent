package com.example.mobileagent.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * صفحه‌ی تنظیمات دستیار صوتی (نسخه‌ی دکمه‌ی شناور).
 *
 * نکته مهم: هر بار که این صفحه از پس‌زمینه برمی‌گرده (Resume)،
 * مجوز overlay دوباره چک می‌شه (چون کاربر توی تنظیمات بوده).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    onBack: () -> Unit
) {
    val ctx = LocalContext.current

    var hasOverlayPerm by remember { mutableStateOf(canDrawOverlays(ctx)) }
    var serviceEnabled by remember { mutableStateOf(FloatingButtonService.isRunning) }

    // هر بار که صفحه Resume می‌شه (از تنظیمات برگشتیم)، مجوز رو چک کن
    LifecycleResumeEffect(Unit) {
        hasOverlayPerm = canDrawOverlays(ctx)
        serviceEnabled = FloatingButtonService.isRunning
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دستیار صوتی") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت")
                    }
                },
                actions = {
                    // دکمه‌ی refresh دستی
                    IconButton(onClick = {
                        hasOverlayPerm = canDrawOverlays(ctx)
                        serviceEnabled = FloatingButtonService.isRunning
                    }) {
                        Icon(Icons.Default.Refresh, "بروزرسانی")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // بخش ۱ — توضیحات
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Mic,
                        null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "دکمه‌ی شناور میکروفون",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "یه دکمه‌ی گرد روی همه‌ی اپ‌ها. روش بزن، " +
                            "حرف بزن، کار انجام می‌شه.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // بخش ۲ — مجوز overlay
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "۱. اجازه‌ی نمایش روی اپ‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (hasOverlayPerm)
                            "✅ داده شده — می‌تونی دکمه رو روشن کنی"
                        else
                            "❌ داده نشده — از دکمه‌ی زیر فعال کن",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasOverlayPerm)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val i = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                ctx.startActivity(i)
                            } catch (_: Exception) { }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (hasOverlayPerm) "تنظیمات مجوز" 
                            else "باز کردن تنظیمات"
                        )
                    }
                }
            }

            // بخش ۳ — کلید سرویس
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "۲. دکمه‌ی شناور",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when {
                                !hasOverlayPerm -> "اول مجوز بالا رو بده"
                                serviceEnabled -> "روشن — دکمه روی صفحه‌ست"
                                else -> "آماده — کلید رو بزن"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = serviceEnabled,
                        enabled = hasOverlayPerm,
                        onCheckedChange = { enabled ->
                            serviceEnabled = enabled
                            if (enabled) {
                                FloatingButtonService.Starter.start(ctx)
                            } else {
                                FloatingButtonService.Starter.stop(ctx)
                            }
                        }
                    )
                }
            }

            // راهنما
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "چطور استفاده کنم؟",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "۱. دکمه‌ی بنفش 🎤 رو بزن\n" +
                        "۲. توی صفحه‌ای که باز می‌شه، حرفت رو بزن\n" +
                        "۳. دکمه رو می‌تونی بکشی و جاش رو عوض کنی\n" +
                        "۴. از هر اپی، حتی وسط بازی، کار می‌کنه",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // هشدار
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    "⚠️ وقتی این سرویس روشنه، یه نوتیف دائمی توی نوار وضعیت داری. " +
                    "برای خاموش کردن، از همون نوتیف یا از این صفحه استفاده کن.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * چک کن مجوز overlay داده شده یا نه.
 */
private fun canDrawOverlays(ctx: Context): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
        Settings.canDrawOverlays(ctx)
    else
        true
