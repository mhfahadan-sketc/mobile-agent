package com.example.mobileagent.agent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * کلاس اتصال به Gemini از طریق Apps Script.
 *
 * جریان:
 *  ۱. متن کاربر گرفته می‌شه
 *  ۲. POST به Apps Script
 *  ۳. Apps Script می‌فرسته به Gemini
 *  ۴. Gemini یه JSON برمی‌گردونه مثل {"action":"call","contact":"علی"}
 *  ۵. این کلاس اون JSON رو به Command تبدیل می‌کنه
 */
class LlmAgent {

    companion object {
        private const val TAG = "LlmAgent"

        // ⚠️ بعداً این رو به GitHub Secrets منتقل می‌کنیم
        private const val API_URL =
            "https://script.google.com/macros/s/AKfycbyPkdLdhXm9dRHZeV0LQ4fLrZhsTlDoPNGCLamVCJeZp4GcWFk1RJdDkC2j9lrRYxrEBw/exec"

        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    sealed interface Result {
        /** LLM یه دستور تشخیص داد */
        data class Cmd(val command: Command) : Result
        /** LLM فقط یه جواب متنی داد */
        data class Chat(val text: String) : Result
        /** خطا در ارتباط یا پردازش */
        data class Error(val message: String) : Result
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * پیام کاربر رو به LLM می‌فرسته و Command رو برمی‌گردونه.
     *
     * @param text متن ورودی کاربر
     * @param history تاریخچه‌ی چت به فرمت (متن، من؟)
     */
    suspend fun parse(
        text: String,
        history: List<Pair<String, Boolean>> = emptyList()
    ): Result = withContext(Dispatchers.IO) {

        try {
            // ساخت بدنه‌ی درخواست
            val historyArray = JSONArray()
            history.takeLast(6).forEach { (t, me) ->
                historyArray.put(JSONObject().apply {
                    put("text", t)
                    put("me", me)
                })
            }

            val bodyObj = JSONObject().apply {
                put("text", text)
                put("history", historyArray)
            }

            val request = Request.Builder()
                .url(API_URL)
                .header("User-Agent", "MobileAgent/1.0 (Android)")
                .post(bodyObj.toString().toRequestBody(JSON_TYPE))
                .build()

            client.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()

                if (!resp.isSuccessful) {
                    Log.w(TAG, "HTTP ${resp.code}: $respBody")
                    return@withContext Result.Error("HTTP ${resp.code}")
                }

                if (respBody.isBlank()) {
                    return@withContext Result.Error("پاسخ خالی از سرور")
                }

                val json = try {
                    JSONObject(respBody)
                } catch (e: Exception) {
                    Log.e(TAG, "JSON parse failed: $respBody", e)
                    return@withContext Result.Error("پاسخ نامعتبر")
                }

                if (json.has("error")) {
                    return@withContext Result.Error(json.optString("error"))
                }

                val llmText = json.optString("text", "").trim()
                if (llmText.isEmpty()) {
                    return@withContext Result.Error("LLM جوابی نداد")
                }

                return@withContext parseAction(llmText)
            }

        } catch (e: Exception) {
            Log.e(TAG, "parse failed", e)
            Result.Error(e.message ?: "خطای شبکه")
        }
    }

    /**
     * متن برگشتی LLM رو به Command تبدیل می‌کنه.
     * LLM باید یه JSON خالص بده مثل {"action":"call","contact":"علی"}
     */
    private fun parseAction(raw: String): Result {
        // پاک‌سازی markdown احتمالی
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }

        // اگه JSON نبود، به‌عنوان chat برمی‌گردونیم
        if (!s.startsWith("{")) {
            return Result.Chat(s)
        }

        return try {
            val obj = JSONObject(s)
            val action = obj.optString("action", "chat")

            when (action) {
                "call" -> {
                    val c = obj.optString("contact", "").trim()
                    if (c.isEmpty()) Result.Error("اسم مخاطب نیامده")
                    else Result.Cmd(Command.Call(c))
                }

                "sms" -> {
                    val c = obj.optString("contact", "").trim()
                    val b = obj.optString("body", "").trim()
                    if (c.isEmpty()) Result.Error("اسم مخاطب نیامده")
                    else Result.Cmd(Command.Sms(c, b.ifEmpty { "سلام" }))
                }

                "whatsapp" -> {
                    val c = obj.optString("contact", "").trim()
                    val b = obj.optString("body", "").trim().ifEmpty { null }
                    if (c.isEmpty()) Result.Error("اسم مخاطب نیامده")
                    else Result.Cmd(Command.WhatsApp(c, b))
                }

                "open_app" -> {
                    val n = obj.optString("name", "").trim()
                    if (n.isEmpty()) Result.Error("اسم اپ نیامده")
                    else Result.Cmd(Command.OpenApp(n))
                }

                "web_search" -> {
                    val q = obj.optString("query", "").trim()
                    if (q.isEmpty()) Result.Error("عبارت جستجو نیامده")
                    else Result.Cmd(Command.WebSearch(q))
                }

                "alarm" -> {
                    val h = obj.optInt("hour", -1)
                    val m = obj.optInt("minute", 0)
                    if (h < 0 || h > 23) Result.Error("ساعت نامعتبر")
                    else Result.Cmd(Command.SetAlarm(h, m.coerceIn(0, 59)))
                }

                "scan_malware" -> Result.Cmd(Command.ScanMalware)
                "find_duplicates" -> Result.Cmd(Command.FindDuplicates)
                "delete_duplicates" -> Result.Cmd(Command.DeleteDuplicates)
                "find_junk" -> Result.Cmd(Command.FindJunk)
                "delete_junk" -> Result.Cmd(Command.DeleteJunk)
                "clean_cache" -> Result.Cmd(Command.CleanCache)
                "analyze_storage" -> Result.Cmd(Command.AnalyzeStorage)
                "help" -> Result.Cmd(Command.Help)

                "chat" -> {
                    val t = obj.optString("text", "").trim()
                    if (t.isEmpty()) Result.Error("پاسخ متنی خالی")
                    else Result.Chat(t)
                }

                else -> Result.Chat(s)
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseAction failed: $s", e)
            Result.Chat(s)
        }
    }
}
