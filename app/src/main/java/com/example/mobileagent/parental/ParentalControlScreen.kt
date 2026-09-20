package com.example.mobileagent.parental

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * صفحه‌ی کنترلر والدین.
 *
 * کارها:
 *  - روشن/خاموش کردن کل سیستم
 *  - تنظیم پین
 *  - لیست اپ‌ها + آمار امروز + تنظیم محدودیت
 *  - لاگ اپ‌هایی که امروز باز شدن
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalControlScreen(
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ParentalStore(ctx) }
    val usageHelper = remember { UsageStatsHelper(ctx) }

    var enabled by remember { mutableStateOf(store.enabled) }
    var softBlock by remember { mutableStateOf(store.softBlock) }
    var hasUsagePermission by remember { mutableStateOf(usageHelper.hasPermission()) }
    var hasPin by remember { mutableStateOf(store.pin != null) }

    var apps by remember { mutableStateOf<List<AppUsageInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showLimitDialog by remember { mutableStateOf<AppUsageInfo?>(null) }

    // لود کردن لیست اپ‌ها
    fun reload() {
        scope.launch {
            loading = true
            val list = withContext(Dispatchers.IO) {
                val base = usageHelper.getUserApps()
                base.map {
                    it.copy(limitMinutes = store.limitFor(it.packageName))
                }
            }
            apps = list
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        hasUsagePermission = usageHelper.hasPermission()
        hasPin = store.pin != null
        reload()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کنترلر والدین") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ═══════════ بخش ۱: وضعیت کل
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "کنترلر فعال",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (enabled) "استفاده از اپ‌ها پایش می‌شه"
                                else "غیرفعال",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { v ->
                                enabled = v
                                store.enabled = v
                                if (v) {
                                    AppLimitService.Starter.start(ctx)
                                } else {
                                    AppLimitService.Starter.stop(ctx)
                                }
                            }
                        )
                    }
                }
            }

            // ═══════════ بخش ۲: پرمیشن آمار استفاده
            if (!hasUsagePermission) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "⚠️ نیاز به دسترسی",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "بدون این دسترسی، نمی‌تونم بفهمم چه اپی چقدر استفاده شده.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { ctx.startActivity(i) } catch (_: Exception) { }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("باز کردن تنظیمات")
                            }
                        }
                    }
                }
            }

            // ═══════════ بخش ۳: پین والدین
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "پین والدین",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (hasPin) "تنظیم شده"
                                else "تنظیم نشده",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showPinDialog = true }) {
                            Text(if (hasPin) "تغییر" else "تنظیم")
                        }
                    }
                }
            }

            // ═══════════ بخش ۴: حالت soft
            item {
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
                                "حالت نرم",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "فقط هشدار بده، بلاک نکن",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = softBlock,
                            onCheckedChange = { v ->
                                softBlock = v
                                store.softBlock = v
                            }
                        )
                    }
                }
            }

            // ═══════════ بخش ۵: عنوان لیست
            item {
                Text(
                    "اپ‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "برای تنظیم محدودیت، روی هر اپ بزن",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ═══════════ بخش ۶: لیست اپ‌ها
            if (loading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            } else if (apps.isEmpty()) {
                item {
                    Text(
                        "هیچ اپی پیدا نشد",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(apps, key = { it.packageName }) { app ->
                    AppLimitCard(
                        app = app,
                        onClick = { showLimitDialog = app }
                    )
                }
            }
        }
    }

    // دیالوگ پین
    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onSave = { newPin ->
                store.pin = newPin
                hasPin = true
                showPinDialog = false
            }
        )
    }

    // دیالوگ محدودیت
    showLimitDialog?.let { app ->
        LimitSetupDialog(
            app = app,
            onDismiss = { showLimitDialog = null },
            onSave = { minutes ->
                store.setLimit(app.packageName, minutes)
                showLimitDialog = null
                reload()
            }
        )
    }
}

// ═══════════════════════════════════════
//  کارت اپ
// ═══════════════════════════════════════

@Composable
private fun AppLimitCard(
    app: AppUsageInfo,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val hasLimit = app.limitMinutes > 0

    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isOverLimit)
                cs.errorContainer.copy(alpha = 0.4f)
            else cs.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
                if (hasLimit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            null,
                            tint = if (app.isOverLimit) cs.error else cs.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "${app.usedMinutes}/${app.limitMinutes}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (app.isOverLimit) cs.error else cs.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        "${app.usedMinutes} دقیقه",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            if (hasLimit) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { app.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = if (app.isOverLimit) cs.error else cs.primary,
                    trackColor = cs.surfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════
//  دیالوگ پین
// ═══════════════════════════════════════

@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیم پین والدین") },
        text = {
            Column {
                Text(
                    "پینی ۴ تا ۶ رقمی برای تأیید والدین تنظیم کن",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("پین") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("تأیید پین") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> error = "پین باید حداقل ۴ رقم باشه"
                    pin != confirm -> error = "پین‌ها یکی نیستن"
                    else -> onSave(pin)
                }
            }) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("لغو") }
        }
    )
}

// ═══════════════════════════════════════
//  دیالوگ محدودیت
// ═══════════════════════════════════════

@Composable
private fun LimitSetupDialog(
    app: AppUsageInfo,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var minutes by remember {
        mutableStateOf(if (app.limitMinutes > 0) app.limitMinutes else 30)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column {
                Text(
                    "چند دقیقه در روز مجاز باشه؟",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    if (minutes > 0) "$minutes دقیقه" else "بدون محدودیت",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))

                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt() },
                    valueRange = 0f..240f,
                    steps = 23,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(15, 30, 60, 120).forEach { preset ->
                        TextButton(onClick = { minutes = preset }) {
                            Text("$preset دقیقه")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "استفاده‌ی امروز: ${app.usedMinutes} دقیقه",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(minutes) }) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onSave(0) // حذف محدودیت
            }) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("حذف")
            }
        }
    )
}
