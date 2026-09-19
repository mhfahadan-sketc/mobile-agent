package com.example.mobileagent.call

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * اعلام تماس‌گیرنده با صدا (TTS).
 *
 * وقتی کسی زنگ می‌زنه، اسم یا شماره‌ش رو با صدای بلند می‌گه:
 *  «علی احمدی داره زنگ می‌زنه»
 */
class CallAnnouncer(ctx: Context) {

    companion object {
        private const val TAG = "CallAnnouncer"
    }

    private val appCtx = ctx.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    init {
        tts = TextToSpeech(appCtx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("fa", "IR"))
                ready = r != TextToSpeech.LANG_MISSING_DATA &&
                        r != TextToSpeech.LANG_NOT_SUPPORTED

                if (!ready) {
                    // fallback: هر زبانی که پشتیبانی می‌شه
                    tts?.setLanguage(Locale.getDefault())
                    ready = true
                }

                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)

                // اگه چیزی توی صف بود، الان بگو
                pending?.let {
                    speak(it)
                    pending = null
                }
            } else {
                Log.e(TAG, "TTS init failed")
            }
        }
    }

    /**
     * اسم یا شماره‌ی تماس‌گیرنده رو اعلام کن.
     */
    fun announceCaller(nameOrNumber: String) {
        val text = "تماس از $nameOrNumber"
        speak(text)
    }

    /**
     * یه پیام دلخواه بگو.
     */
    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "call_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "speak failed", e)
        }
    }

    fun stop() {
        try { tts?.stop() } catch (_: Exception) { }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) { }
        tts = null
        ready = false
    }
}
