package com.example.mobileagent.voice

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * صفحه‌ی تنظیمات صدای پس‌زمینه.
 *
 * کارها:
 *  - چک کردن وجود مدل Vosk
 *  - دانلود مدل از اینترنت (بار اول)
 *  - روشن/خاموش کردن سرویس گوش دادن
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var modelReady by remember { mutableStateOf(false) }
    var serviceEnabled by remember { mutableStateOf(WakeWordService.isRunning) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // چک کن مدل نصب هست یا نه
    LaunchedEffect(Unit) {
        modelReady = withContext(Dispatchers.IO) {
            isModelInstalled(ctx)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دستیار صوتی") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
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
                            "چیکار می‌کنه؟",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "وقتی روشنه، همیشه به کلمه‌ی «دستیار» گوش می‌ده. " +
                            "کافیه بگی «دستیار زنگ بزن به علی» بدون اینکه اپ رو باز کنی.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // بخش ۲ — وضعیت مدل
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (modelReady) Icons.Default.CheckCircle
                            else Icons.Default.Download,
                            null,
                            tint = if (modelReady) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "مدل تشخیص صدا",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (modelReady) "نصب‌شده ✅"
                                else "نصب نشده — یه بار دانلود (۴۰ مگابایت)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!modelReady) {
                        Spacer(Modifier.height(12.dp))
                        if (downloading) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${(downloadProgress * 100).toInt()}% در حال دانلود…",
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        downloading = true
                                        errorText = null
                                        downloadProgress = 0f
                                        val ok = downloadModel(ctx) { p ->
                                            downloadProgress = p
                                        }
                                        downloading = false
                                        if (ok) {
                                            modelReady = true
                                        } else {
                                            errorText = "دانلود نشد. اینترنت رو چک کن."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("دانلود مدل")
                            }
                        }
                    }
                }
            }

            // بخش ۳ — کلید روشن/خاموش
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
                            "گوش دادن دائمی",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "سرویس توی پس‌زمینه فعال باشه",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = serviceEnabled,
                        enabled = modelReady,
                        onCheckedChange = { enabled ->
                            serviceEnabled = enabled
                            if (enabled) {
                                WakeWordService.Starter.start(ctx)
                            } else {
                                WakeWordService.Starter.stop(ctx)
                            }
                        }
                    )
                }
            }

            // نمایش خطا
            errorText?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // هشدار باتری
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    "⚠️ توجه: فعال بودن این سرویس باعث می‌شه باتری روزی ۱۵-۲۵٪ بیشتر مصرف شه.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  دانلود و استخراج مدل Vosk
// ═══════════════════════════════════════════════════

private const val MODEL_DIR = "vosk-model"

/**
 * URL مدل فارسی Vosk — از سایت رسمی alphacephei.com
 * مدل small: ~40MB
 */
private const val MODEL_URL =
    "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.5.zip"

/**
 * چک کن مدل از قبل نصب هست یا نه.
 */
private fun isModelInstalled(ctx: Context): Boolean {
    val dir = File(ctx.filesDir, MODEL_DIR)
    return dir.exists() && dir.listFiles()?.isNotEmpty() == true
}

/**
 * مدل رو دانلود و استخراج کن.
 * @param onProgress 0..1
 * @return true اگه موفق بود
 */
private suspend fun downloadModel(
    ctx: Context,
    onProgress: (Float) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    val tempZip = File(ctx.cacheDir, "model.zip")
    val targetDir = File(ctx.filesDir, MODEL_DIR)

    try {
        // ۱. دانلود
        val url = URL(MODEL_URL)
        val conn = url.openConnection()
        conn.connect()
        val total = conn.contentLengthLong
        val input = conn.getInputStream()

        tempZip.outputStream().use { out ->
            val buf = ByteArray(8192)
            var downloaded = 0L
            var read: Int
            while (input.read(buf).also { read = it } > 0) {
                out.write(buf, 0, read)
                downloaded += read
                if (total > 0) {
                    onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        input.close()

        // ۲. استخراج
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        java.util.zip.ZipInputStream(tempZip.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // مسیر رو از zip حذف کن (چون یه پوشه‌ی اضافی داره)
                val name = entry.name.substringAfter('/')
                if (name.isNotEmpty()) {
                    val f = File(targetDir, name)
                    if (entry.isDirectory) {
                        f.mkdirs()
                    } else {
                        f.parentFile?.mkdirs()
                        f.outputStream().use { o ->
                            zip.copyTo(o)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // ۳. پاک کردن فایل zip
        tempZip.delete()

        onProgress(1f)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        tempZip.delete()
        false
    }
}
