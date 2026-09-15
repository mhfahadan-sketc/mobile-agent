package com.example.mobileagent.agent

import android.content.Context
import android.provider.ContactsContract

class ContactResolver(private val context: Context) {

    /**
     * بهترین تطابق رو پیدا می‌کنه.
     * معیار:
     *  ۱. اگر شماره بود، مستقیم
     *  ۲. اگر اسم دقیق بود، بهترین
     *  ۳. اگر قسمتی از اسم بود، امتیاز نسبی
     *  ۴. اگر کلمات جدا شده بودن (مثل «علی احمدی» و کاربر گفته «علی»)، هر کلمه جداگانه چک می‌شه
     *  ۵. اگر کاربر تایپ اشتباه داشت (مثل «mhani» به جای «مهانی»)، فاصله‌ی لِوِنشتاین حساب می‌شه
     */
    fun resolve(query: String): String? {
        val q = Parser.normalize(query)
        if (q.isEmpty()) return null

        // ۱. اگر کاربر خودش شماره داده
        val digits = q.filter { it.isDigit() || it == '+' }
        if (digits.length >= 5 && digits.length >= q.length - 2) return digits

        var best: Pair<Int, String>? = null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null, null
            )?.use { c ->
                val nameCol = c.getColumnIndex(projection[0])
                val numCol = c.getColumnIndex(projection[1])
                while (c.moveToNext()) {
                    val name = c.getString(nameCol) ?: continue
                    val num = c.getString(numCol) ?: continue
                    val score = scoreName(Parser.normalize(name), q)
                    if (score > (best?.first ?: 0)) best = score to num
                }
            }
        } catch (_: Exception) { }
        return best?.second
    }

    /**
     * امتیاز شباهت بین دو اسم.
     * ۱۰۰ = یکسان
     * ۸۰ = یکی با دیگری شروع می‌شه
     * ۷۰ = یکی توی دیگری هست
     * ۶۰ = کلمه‌ی مشترک دارن (مثلاً «علی احمدی» و «علی»)
     * ۴۰ = شباهت لِوِنشتاین قابل قبول
     * ۰ = هیچ
     */
    private fun scoreName(name: String, query: String): Int {
        // ۱. یکسان
        if (name == query) return 100

        // ۲. یکی با دیگری شروع می‌شه
        if (name.startsWith(query) || query.startsWith(name)) return 80

        // ۳. یکی توی دیگری هست
        if (name.contains(query) || query.contains(name)) return 70

        // ۴. تقسیم به کلمات و مقایسه
        val nameWords = name.split(" ").filter { it.length >= 2 }
        val queryWords = query.split(" ").filter { it.length >= 2 }

        if (nameWords.isNotEmpty() && queryWords.isNotEmpty()) {
            var matchedWords = 0
            for (qw in queryWords) {
                for (nw in nameWords) {
                    if (nw == qw || nw.startsWith(qw) || qw.startsWith(nw)) {
                        matchedWords++
                        break
                    }
                }
            }
            if (matchedWords > 0 && matchedWords == queryWords.size) {
                return 60 + (matchedWords * 5).coerceAtMost(15)
            }
        }

        // ۵. فاصله‌ی لِوِنشتاین — برای تایپ اشتباه
        val minLen = minOf(name.length, query.length)
        if (minLen >= 4) {
            val dist = levenshtein(name, query)
            val threshold = when {
                minLen <= 5 -> 1
                minLen <= 8 -> 2
                else -> 3
            }
            if (dist <= threshold) {
                // هرچه فاصله کمتر، امتیاز بیشتر
                return 50 - (dist * 5).coerceAtLeast(0)
            }
        }

        return 0
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }
}
