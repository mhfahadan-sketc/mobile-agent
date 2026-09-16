package com.example.mobileagent.storage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.example.mobileagent.core.ProgressChannel
import com.example.mobileagent.core.ScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class JunkFile(
    val file: File,
    val reason: String,
    val size: Long
)

data class CacheInfo(
    val packageName: String,
    val label: String,
    val bytes: Long
)

data class StorageReport(
    val total: Long,
    val free: Long,
    val used: Long,
    val byCategory: Map<String, Long>
)

class JunkCleaner(private val ctx: Context) {

    suspend fun findJunk(): List<JunkFile> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        val roots = listOf(
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "DCIM/.thumbnails"),
            File(Environment.getExternalStorageDirectory(), "Android/media"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/.Statuses"),
            File(Environment.getExternalStorageDirectory(), ".trash"),
            File(Environment.getExternalStorageDirectory(), "tmp"),
            ctx.externalCacheDir
        ).filterNotNull().filter { it.exists() }

        val all = mutableListOf<File>()
        for (r in roots) {
            try {
                r.walkTopDown().maxDepth(6)
                    .filter { it.isFile }
                    .forEach { all += it }
            } catch (_: Exception) { }
        }

        val out = mutableListOf<JunkFile>()
        all.forEachIndexed { i, f ->
            if (i % 50 == 0) {
                ProgressChannel.emit(
                    ScanProgress.Scanning(
                        current = i + 1,
                        total = all.size,
                        label = f.name,
                        phase = "junk"
                    )
                )
            }

            val age = (now - f.lastModified()) / 86_400_000
            val name = f.name.lowercase()
            val size = f.length()

            val reason = when {
                size == 0L -> "فایل خالی"
                name.endsWith(".tmp") && age > 7 -> "tmp قدیمی"
                name.endsWith(".part") -> "دانلود ناتمام"
                name.endsWith(".log") && age > 30 -> "لاگ قدیمی"
                name.startsWith("~") -> "فایل موقت"
                f.absolutePath.contains("/.thumbnails/") -> "thumbnail"
                f.absolutePath.contains("/.trash/") -> "سطل بازیافت"
                else -> null
            }

            if (reason != null) out += JunkFile(f, reason, size)
        }

        ProgressChannel.emit(
            ScanProgress.Done("${out.size} فایل", out.size)
        )

        out.sortedByDescending { it.size }
    }

    suspend fun deleteJunk(list: List<JunkFile>): Pair<Int, Long> =
        withContext(Dispatchers.IO) {
            val safe = setOf(
                "فایل خالی",
                "tmp قدیمی",
                "دانلود ناتمام",
                "فایل موقت",
                "thumbnail"
            )

            var count = 0
            var freed = 0L

            for (j in list) {
                if (j.reason !in safe) continue
                val size = j.size
                if (j.file.delete()) {
                    count++
                    freed += size
                    try {
                        ctx.contentResolver.delete(
                            MediaStore.Files.getContentUri("external"),
                            "${MediaStore.MediaColumns.DATA} = ?",
                            arrayOf(j.file.absolutePath)
                        )
                    } catch (_: Exception) { }
                }
            }
            count to freed
        }

    suspend fun analyze(): StorageReport = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val used = total - free

        val cats = mapOf(
            "عکس" to setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp"),
            "فیلم" to setOf("mp4", "mkv", "avi", "mov", "3gp", "webm"),
            "صوت" to setOf("mp3", "wav", "m4a", "ogg", "flac"),
            "اسناد" to setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt"),
            "فشرده" to setOf("zip", "rar", "7z", "tar", "gz", "apk")
        )

        val sizes = mutableMapOf<String, Long>()
        cats.keys.forEach { sizes[it] = 0 }

        try {
            File(Environment.getExternalStorageDirectory().path)
                .walkTopDown()
                .onEnter { it.name != "Android" }
                .filter { it.isFile }
                .forEach { f ->
                    val ext = f.extension.lowercase()
                    for ((cat, exts) in cats) {
                        if (ext in exts) {
                            sizes[cat] = sizes[cat]!! + f.length()
                            break
                        }
                    }
                }
        } catch (_: Exception) { }

        StorageReport(total, free, used, sizes)
    }

    suspend fun appCacheSizes(): List<CacheInfo> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val out = mutableListOf<CacheInfo>()

        val apps = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        else
            @Suppress("DEPRECATION") pm.getInstalledApplications(0)

        for (app in apps) {
            val isSys = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (isSys && app.packageName != ctx.packageName) continue

            val size = if (app.packageName == ctx.packageName)
                ctx.cacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
            else 0L

            if (size > 0) {
                out += CacheInfo(
                    app.packageName,
                    pm.getApplicationLabel(app).toString(),
                    size
                )
            }
        }
        out.sortedByDescending { it.bytes }
    }

    suspend fun clearOwnCache(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).forEach { dir ->
            dir.walkTopDown().filter { it.isFile }.forEach {
                val s = it.length()
                if (it.delete()) freed += s
            }
        }
        freed
    }
}
