package com.example.mobileagent.parental

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileagent.ui.theme.MobileAgentTheme

/**
 * صفحه‌ی مسدود.
 *
 * وقتی کاربر از حد مجاز یه اپ گذشته، این Activity باز می‌شه.
 * کلید Back رو بلاک می‌کنه که نتونه در بره.
 */
class LimitOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_USED = "used"
        const val EXTRA_LIMIT = "limit"
    }

    private lateinit var store: ParentalStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ParentalStore(this)

        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return finish()
        val usedMin = intent.getIntExtra(EXTRA_USED, 0)
        val limitMin = intent.getIntExtra(EXTRA_LIMIT, 0)

        val appLabel = try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }

        setContent {
            MobileAgentTheme {
                BlockScreen(
                    appLabel = appLabel,
                    usedMinutes = usedMin,
                    limitMinutes = limitMin,
                    onGoHome = { goHome() },
                    onUnlock = { pin ->
                        if (pin == store.pin) {
                            // والدین تأیید شد → برای ۵ دقیقه اجازه بده
                            AppLimitServiceGuard.tempAllow(pkg, 5)
                            finish()
                            true
                        } else {
                            false
                        }
                    }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            goHome()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun goHome() {
        try {
            val i = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(i)
        } catch (_: Exception) { }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }
}

// ═══════════════════════════════════════
//  UI
// ═══════════════════════════════════════

@Composable
private fun BlockScreen(
    appLabel: String,
    usedMinutes: Int,
    limitMinutes: Int,
    onGoHome: () -> Unit,
    onUnlock: (String) -> Boolean
) {
    var showPinDialog by remember { mutableStateOf(false) }

    val cs = MaterialTheme.colorScheme

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF2D1B4E)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // آیکون قفل
            Surface(
                shape = CircleShape,
                color = Color(0xFF7C4DFF).copy(alpha = 0.2f),
                modifier = Modifier.size(140.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Lock,
                        null,
                        tint = Color(0xFFB39DFF),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "وقت تمومه! ⏰",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "زمان استفاده از این برنامه به پایان رسیده",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // کارت جزئیات
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        appLabel,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$usedMinutes",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00D4FF)
                            )
                            Text(
                                "دقیقه استفاده",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$limitMinutes",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB39DFF)
                            )
                            Text(
                                "دقیقه مجاز",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // دکمه‌ی اصلی — برو خونه
            Button(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C4DFF)
                )
            ) {
                Text(
                    "برگرد به صفحه‌ی اصلی",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            // دکمه‌ی والدین
            OutlinedButton(
                onClick = { showPinDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Text("من والدین هستم")
            }
        }
    }

    // دیالوگ پین
    if (showPinDialog) {
        PinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = onUnlock
        )
    }
}

@Composable
private fun PinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رمز والدین رو وارد کن") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { c -> c.isDigit() }.take(6)
                        error = false
                    },
                    placeholder = { Text("رمز ۴ تا ۶ رقمی") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "رمز اشتباهه",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (onConfirm(pin)) {
                    // پنجره بسته می‌شه خودکار
                } else {
                    error = true
                    pin = ""
                }
            }) {
                Text("تأیید")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("لغو")
            }
        }
    )
}

// ═══════════════════════════════════════
//  راه‌حل ساده برای اجازه‌ی موقت
// ═══════════════════════════════════════

/**
 * یه حالت سراسری که پکیج‌هایی که برای مدتی اجازه‌ی موقت دارن رو نگه می‌داره.
 */
object AppLimitServiceGuard {
    private val tempAllowed = mutableMapOf<String, Long>()  // pkg → زمان انقضا

    fun tempAllow(pkg: String, minutes: Int) {
        tempAllowed[pkg] = System.currentTimeMillis() + minutes * 60_000L
    }

    fun isAllowed(pkg: String): Boolean {
        val exp = tempAllowed[pkg] ?: return false
        if (System.currentTimeMillis() > exp) {
            tempAllowed.remove(pkg)
            return false
        }
        return true
    }
}
