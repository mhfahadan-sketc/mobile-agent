package com.example.mobileagent.storage

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.example.mobileagent.core.ProgressChannel
import com.example.mobileagent.core.ScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class DuplicateGroup(
    val hash: String,
    val files: List<File>,
    val sizeEach: Long
) {
    val wasted: Long get() = sizeEach * (files.size - 1).coerceAtLeast(0)
}

enum class KeepPolicy { OLDEST, NEWEST }

class DuplicateFinder(private val ctx: Context) {

    suspend fun find(minSize: Long = 4 * 1024): List<DuplicateGroup> =
        withContext(Dispatchers.IO) {
            val all = mutableListOf<File>()

            val roots = listOf(
                Environment.getExternalStorageDirectory(),
                File("/storage/emulated/0/Download"),
                File("/storage/emulated/0/DCIM"),
                File("/storage/emulated/0/Pictures"),
                File("/storage/emulated/0/WhatsApp")
            ).filter { it.exists() }

            for (root in roots) {
                try {
                    root.walkTopDown().maxDepth(8)
                        .onEnter { it.name != "Android" && it.name != ".thumbnails" }
                        .filter { it.isFile && it.length() >= minSize && it.canRead() }
                        .forEach { all += it }
                } catch (_: Exception) { }
            }

            val bySize = all.groupBy { it.length() }.filter { it.value.size > 1 }
            val total = bySize.values.sumOf { it.size }
            var done = 0

            val quick = mutableMapOf<String, MutableList<File>>()
            for ((_, group) in bySize) {
                for (f in group) {
                    val h = "${f.length()}:${quickHash(f)}"
                    quick.getOrPut(h) { mutableListOf() }.add(f)
                    done++
                    ProgressChannel.emit(
                        ScanProgress.Scanning(
                            current = done,
                            total = total,
                            label = f.name,
                            phase = "duplicates"
                        )
                    )
                }
            }

            val result = mutableListOf<DuplicateGroup>()
            for ((_, candidates) in quick) {
                if (candidates.size < 2) continue
                val fullGroups = candidates.groupBy { fullHash(it) }
                for ((h, files) in fullGroups) {
                    if (files.size > 1) {
                        result += DuplicateGroup(h, files, files.first().length())
                    }
                }
            }

            ProgressChannel.emit(
                ScanProgress.Done(
                    summary = "${result.size} گروه",
                    itemCount = result.size
                )
            )

            result.sortedByDescending { it.wasted }
        }

    suspend fun delete(
        groups: List<DuplicateGroup>,
        policy: KeepPolicy = KeepPolicy.NEWEST
    ): Pair<Int, Long> = withContext(Dispatchers.IO) {
        var count = 0
        var freed = 0L

        for (g in groups) {
            val keep = when (policy) {
                KeepPolicy.OLDEST -> g.files.minByOrNull { it.lastModified() }
                KeepPolicy.NEWEST -> g.files.maxByOrNull { it.lastModified() }
            } ?: continue

            for (f in g.files) {
                if (f == keep) continue
                val size = f.length()
                if (f.delete()) {
                    try {
                        ctx.contentResolver.delete(
                            MediaStore.Files.getContentUri("external"),
                            "${MediaStore.MediaColumns.DATA} = ?",
                            arrayOf(f.absolutePath)
                        )
                    } catch (_: Exception) { }
                    count++
                    freed += size
                }
            }
        }
        count to freed
    }

    private fun quickHash(f: File): String = try {
        val md = MessageDigest.getInstance("MD5")
        f.inputStream().use { input ->
            val buf = ByteArray(1024 * 1024)
            val n = input.read(buf)
            if (n > 0) md.update(buf, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }

    private fun fullHash(f: File): String = try {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var n = input.read(buf)
            while (n > 0) {
                md.update(buf, 0, n)
                n = input.read(buf)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }
}
