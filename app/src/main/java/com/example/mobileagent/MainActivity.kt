package com.example.mobileagent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
        if (Build.VERSION.SDK_INT >= 33)
            addIfMissing(Manifest.permission.POST_NOTIFICATIONS)

        if (need.isNotEmpty()) reqPerms.launch(need.toTypedArray())
    }
}
