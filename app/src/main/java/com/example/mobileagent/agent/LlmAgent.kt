package com.example.mobileagent.agent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * بهبود یافته:
 *  - Retry خودکار (تا ۳ بار)
 *  - Timeout بیشتر (cold start Apps Script)
 *  - Backoff تصاعدی بین تلاش‌ها
 */
class LlmAgent {

    companion object {
        private const val TAG = "LlmAgent"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_BASE_MS = 800L

        // ⚠️ بعداً این رو به GitHub Secrets منتقل می‌کنیم
        private const val API_URL =
            "https://script.google.com/macros/s/AKfycbyPkdLdhXm9dRHZeV0LQ4fLrZhsTlDoPNGCLamVCJeZp4GcWFk1RJdDkC2j9lrRYxrEBw/exec"

        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    sealed interface Result {
        data class Cmd(val command: Command) : Result
        data class Chat(val text: String) : Result
        data class Error(val message: String) : Result
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun parse(
        text: String,
        history: List<Pair<String, Boolean>> = emptyList()
    ): Result = withContext(Dispatchers.IO) {

        var lastError: String = "unknown"

        for (attempt in 0 until MAX_RETRIES) {
            if (attempt > 0) {
                // backoff تصاعدی: ۸۰۰ms، ۱۶۰۰ms
                delay(RETRY_DELAY_BASE_MS * attempt)
                Log.d(TAG, "Retry attempt ${attempt + 1}")
            }

            val result = tryRequest(text, history)
            when (result) {
                is Result.Error -> {
                    lastError = result.message
                    Log.w(TAG, "Attempt ${attempt + 1} failed: $lastError")
                    // اگه خطا 4xx بود، بی‌فایده‌ست retry
                    if (lastError.startsWith("HTTP 4")) break
                }
                else -> return@withContext result
            }
        }

        Result.Error(lastError)
    }

    private fun tryRequest(
        text: String,
        history: List<Pair<String, Boolean>>
    ): Result {
        return try {
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
                    return Result.Error("HTTP ${resp.code}")
                }

                if (respBody.isBlank()) {
                    return Result.Error("پاسخ خالی از سرور")
                }

                val json = try {
                    JSONObject(respBody)
                } catch (e: Exception) {
                    Log.e(TAG, "JSON parse failed: $respBody", e)
                    return Result.Error("پاسخ نامعتبر")
                }

                if (json.has("error")) {
                    return Result.Error(json.optString("error"))
                }

                val llmText = json.optString("text", "").trim()
                if (llmText.isEmpty()) {
                    return Result.Error("LLM جوابی نداد")
                }

                return parseAction(llmText)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Request failed", e)
            Result.Error(e.message ?: "خطای شبکه")
        }
    }

    private fun parseAction(raw: String): Result {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }

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
