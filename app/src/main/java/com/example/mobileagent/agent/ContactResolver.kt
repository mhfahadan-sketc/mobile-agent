package com.example.mobileagent.agent

import android.content.Context
import android.provider.ContactsContract

class ContactResolver(private val context: Context) {

    fun resolve(query: String): String? {
        val q = Parser.normalize(query)
        if (q.isEmpty()) return null

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
                    val nName = Parser.normalize(name)
                    val score = when {
                        nName == q -> 100
                        nName.startsWith(q) -> 80
                        nName.contains(q) -> 60
                        q.contains(nName) -> 40
                        else -> 0
                    }
                    if (score > (best?.first ?: 0)) best = score to num
                }
            }
        } catch (_: Exception) { }
        return best?.second
    }
}
