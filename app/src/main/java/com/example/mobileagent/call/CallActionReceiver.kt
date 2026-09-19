package com.example.mobileagent.call

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import com.example.mobileagent.voice.VoiceRecognitionActivity

/**
 * گیرنده‌ی دکمه‌های نوتیف تماس.
 *
 * سه تا دکمه داره:
 *  - جواب بده → تماس رو وصل می‌کنه
 *  - رد کن → تماس رو قطع می‌کنه
 *  - پیام سفارشی → VoiceRecognitionActivity باز می‌شه که بگی متن پیام
 */
class CallActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallActionReceiver"

        const val ACTION_ANSWER = "com.example.mobileagent.CALL_ANSWER"
        const val ACTION_REJECT = "com.example.mobileagent.CALL_REJECT"
        const val ACTION_CUSTOM_SMS = "com.example.mobileagent.CALL_SMS"

        const val EXTRA_PENDING_SMS_NUMBER = "pending_sms_number"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Action: $action")

        when (action) {
            ACTION_ANSWER -> answerCall(context)
            ACTION_REJECT -> rejectCall(context)
            ACTION_CUSTOM_SMS -> customSms(context, intent)
        }
    }

    // ═══════════════════════════════════════
    //  جواب دادن به تماس
    // ═══════════════════════════════════════

    private fun answerCall(context: Context) {
        try {
            val tm = context.getSystemService(TelecomManager::class.java)
            if (tm == null) {
                toast(context, "TelecomManager در دسترس نیست")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.acceptRingingCall()
                Log.i(TAG, "Call accepted")
                dismissNotification(context)
                toast(context, "تماس جواب داده شد ✅")
            } else {
                // API < 26: از طریق intent
                @Suppress("DEPRECATION")
                val i = Intent(Intent.ACTION_MEDIA_BUTTON)
                i.putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_HEADSETHOOK
                ))
                context.sendOrderedBroadcast(i, null)
                dismissNotification(context)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "answerCall permission denied", e)
            toast(context, "اجازه‌ی جواب دادن نداری — از تنظیمات بده")
        } catch (e: Exception) {
            Log.e(TAG, "answerCall failed", e)
            toast(context, "جواب دادن نشد: ${e.message}")
        }
    }

    // ═══════════════════════════════════════
    //  رد کردن تماس
    // ═══════════════════════════════════════

    private fun rejectCall(context: Context) {
        try {
            val tm = context.getSystemService(TelecomManager::class.java)
            if (tm == null) {
                toast(context, "TelecomManager در دسترس نیست")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+
                tm.endCall()
                Log.i(TAG, "Call rejected")
                dismissNotification(context)
                toast(context, "تماس رد شد ❌")
            } else {
                toast(context, "این ویژگی روی اندروید ۹+ کار می‌کنه")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "rejectCall permission denied", e)
            toast(context, "اجازه‌ی رد کردن نداری")
        } catch (e: Exception) {
            Log.e(TAG, "rejectCall failed", e)
            toast(context, "رد کردن نشد: ${e.message}")
        }
    }

    // ═══════════════════════════════════════
    //  پیام سفارشی
    // ═══════════════════════════════════════

    private fun customSms(context: Context, intent: Intent) {
        val number = intent.getStringExtra("number") ?: return
        val name = intent.getStringExtra("name") ?: number

        // اول تماس رو رد کن
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.getSystemService(TelecomManager::class.java)?.endCall()
            }
        } catch (_: Exception) { }

        dismissNotification(context)

        // بعد VoiceRecognitionActivity رو باز کن که بگی متن پیام
        try {
            val i = Intent(context, VoiceRecognitionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_PENDING_SMS_NUMBER, number)
                putExtra("pending_sms_name", name)
            }
            context.startActivity(i)
        } catch (e: Exception) {
            Log.e(TAG, "launch voice failed", e)
            // fallback: یه پیام پیش‌فرض بفرست
            sendDefaultSms(context, number)
        }
    }

    private fun sendDefaultSms(context: Context, number: String) {
        try {
            val sm = if (Build.VERSION.SDK_INT >= 31)
                context.getSystemService(SmsManager::class.java)!!
            else
                @Suppress("DEPRECATION") SmsManager.getDefault()

            sm.sendTextMessage(number, null, "الان نمی‌تونم جواب بدم، بعداً تماس می‌گیرم.", null, null)
            toast(context, "پیام پیش‌فرض فرستاده شد")
        } catch (e: Exception) {
            Log.e(TAG, "sendDefaultSms failed", e)
        }
    }

    // ═══════════════════════════════════════
    //  کمکی
    // ═══════════════════════════════════════

    private fun dismissNotification(context: Context) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.cancel(9001)
        } catch (_: Exception) { }
    }

    private fun toast(context: Context, msg: String) {
        try {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { }
    }
}
