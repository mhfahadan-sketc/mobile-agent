package com.example.mobileagent.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.mobileagent.MainActivity

/**
 * گیرنده‌ی آلارم.
 *
 * وقتی AlarmManager آلارم رو صدا می‌زنه، این receiver فعال می‌شه
 * و یه نوتیف با صدای بلند نشون می‌ده.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "alarm_channel"
        private const val NOTIF_ID = 7001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra("hour", 0)
        val minute = intent.getIntExtra("minute", 0)
        val timeStr = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

        showAlarmNotification(context, timeStr)
    }

    private fun showAlarmNotification(context: Context, timeStr: String) {
        val nm = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "آلارم‌ها",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "آلارم‌های تنظیم‌شده"
                    enableVibration(true)
                    setSound(alarmSound, audioAttrs)
                    enableLights(true)
                }
                nm.createNotificationChannel(channel)
            }
        }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dismiss = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, AlarmReceiver::class.java).apply {
                action = "DISMISS_ALARM"
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ آلارم!")
            .setContentText("ساعت $timeStr")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(open, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .addAction(0, "خاموش", dismiss)
            .build()

        nm.notify(NOTIF_ID, notif)
    }
}
