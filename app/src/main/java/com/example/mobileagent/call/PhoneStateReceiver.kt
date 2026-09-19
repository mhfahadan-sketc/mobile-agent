package com.example.mobileagent.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mobileagent.MainActivity
import com.example.mobileagent.R

/**
 * گیرنده‌ی وضعیت تماس.
 *
 * وقتی کسی زنگ می‌زنه:
 *  ۱. اسمش رو پیدا می‌کنه
 *  ۲. با TTS بلند می‌گه: «تماس از علی احمدی»
 *  ۳. نوتیف با دکمه‌ها نشون می‌ده
 */
class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneStateReceiver"
        private const val CHANNEL_INCOMING = "incoming_call"
        private const val NOTIF_ID_INCOMING = 9001

        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var lastNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                Log.d(TAG, "Incoming call from: $number")
                handleIncoming(context, number)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d(TAG, "Call answered/active")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "Call ended")
                // پاک کردن نوتیف
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(NOTIF_ID_INCOMING)
            }
        }

        lastState = state
    }

    private fun handleIncoming(context: Context, number: String?) {
        if (number.isNullOrBlank()) return
        // نذار دو بار پشت هم اجرا شه
        if (number == lastNumber && lastState == TelephonyManager.EXTRA_STATE_RINGING) return
        lastNumber = number

        // پیدا کردن اسم
        val displayName = resolveContactName(context, number) ?: formatNumber(number)

        // ۱. TTS
        val announcer = CallAnnouncer(context)
        announcer.announceCaller(displayName)

        // ۲. نوتیف
        showIncomingNotification(context, displayName, number)
    }

    private fun resolveContactName(context: Context, number: String): String? {
        var cursor: Cursor? = null
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "resolveContactName failed", e)
            null
        } finally {
            cursor?.close()
        }
    }

    private fun formatNumber(number: String): String {
        // اگه شماره‌ی ایران بود، قشنگ‌تر کن
        return number.trim()
    }

    private fun showIncomingNotification(
        context: Context,
        displayName: String,
        number: String
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)

        // ساخت کانال
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CHANNEL_INCOMING) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_INCOMING,
                        "تماس‌های ورودی",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "اعلام تماس‌های ورودی"
                        enableVibration(true)
                    }
                )
            }
        }

        // Intent باز کردن اپ
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // دکمه‌ی «جواب بده»
        val answerIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_ANSWER
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // دکمه‌ی «رد کن»
        val rejectIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_REJECT
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // دکمه‌ی «پیام سفارشی»
        val smsIntent = PendingIntent.getBroadcast(
            context, 3,
            Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_CUSTOM_SMS
                putExtra("number", number)
                putExtra("name", displayName)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("📞 $displayName")
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .addAction(0, "جواب بده", answerIntent)
            .addAction(0, "رد کن", rejectIntent)
            .addAction(0, "پیام سفارشی", smsIntent)
            .build()

        nm.notify(NOTIF_ID_INCOMING, notif)
    }
}
