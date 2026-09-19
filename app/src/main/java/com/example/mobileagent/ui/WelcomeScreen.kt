package com.example.mobileagent.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileagent.R

/**
 * صفحه‌ی خوش‌آمد — وقتی کاربر اپ رو باز می‌کنه، اینجا نشون داده می‌شه.
 * دکمه‌های سریع برای دستورهای رایج.
 */
@Composable
fun WelcomeScreen(
    onQuickCommand: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    // انیمیشن آیکون
    val infinite = rememberInfiniteTransition(label = "welcome")
    val scale by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        cs.background,
                        cs.primaryContainer.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // آیکون اپ با هاله
            Box(
                Modifier
                    .size(140.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                cs.primary.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.size(100.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "دستیار",
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(1f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "سلام 👋",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "من دستیارتم",
                style = MaterialTheme.typography.titleLarge,
                color = cs.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "هر جوری که راحتی حرف بزن — می‌فهمم",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            Text(
                "چیکار برات بکنم؟",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onBackground
            )

            Spacer(Modifier.height(16.dp))

            // دکمه‌های پیشنهاد
            val quickActions = listOf(
                Triple("📞", "زنگ بزن", "زنگ بزن به "),
                Triple("✉️", "پیام بده", "به مامان پیام بده که "),
                Triple("🚀", "اپ باز کن", "اینستاگرام رو باز کن"),
                Triple("🔎", "جستجو", "گوگل کن "),
                Triple("🛡️", "اسکن امنیتی", "ویروس‌ها رو پیدا کن"),
                Triple("📊", "فضای حافظه", "چقدر فضا اشغال شده")
            )

            // ردیف اول — ۳ تا
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                quickActions.take(3).forEach { (icon, label, cmd) ->
                    WelcomeChip(
                        icon = icon,
                        label = label,
                        onClick = { onQuickCommand(cmd) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ردیف دوم — ۳ تا
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                quickActions.drop(3).forEach { (icon, label, cmd) ->
                    WelcomeChip(
                        icon = icon,
                        label = label,
                        onClick = { onQuickCommand(cmd) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "می‌تونی میکروفون رو بزنی و حرف بزنی 🎤",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WelcomeChip(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = cs.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, fontSize = 26.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface
            )
        }
    }
}
