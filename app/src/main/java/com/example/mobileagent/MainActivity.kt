package com.example.mobileagent

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.example.mobileagent.ui.MobileAgentApp
import com.example.mobileagent.ui.theme.MobileAgentTheme

class MainActivity : ComponentActivity() {

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askPermissions()
        askExactAlarmPermission()
        setContent {
            MobileAgentTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    MobileAgentApp()
                }
            }
        }
    }

    private fun askPermissions() {
        val need = mutableListOf<String>()
        fun addIfMissing(p: String) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                need += p
        }

        addIfMissing(Manifest.permission.CALL_PHONE)
        addIfMissing(Manifest.permission.SEND_SMS)
        addIfMissing(Manifest.permission.READ_CONTACTS)
        addIfMissing(Manifest.permission.RECORD_AUDIO)
        addIfMissing(Manifest.permission.READ_PHONE_STATE)

        if (Build.VERSION.SDK_INT >= 26) {
            addIfMissing(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            addIfMissing(Manifest.permission.READ_CALL_LOG)
        }

        if (need.isNotEmpty()) reqPerms.launch(need.toTypedArray())
    }

    private fun askExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!am.canScheduleExactAlarms()) {
                    val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { startActivity(i) } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
        }
    }
}
