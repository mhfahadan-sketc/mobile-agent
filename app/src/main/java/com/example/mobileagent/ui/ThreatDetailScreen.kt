package com.example.mobileagent.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mobileagent.security.Severity
import com.example.mobileagent.security.Threat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatDetailScreen(threats: List<Threat>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("گزارش امنیتی") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        if (threats.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        null,
                        modifier = Modifier.size(72.dp),
                        tint = Color(0xFF2E7D32)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("هیچ تهدیدی نبود", style = MaterialTheme.typography.titleMedium)
                    Text("گوشیت امنه ✅")
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(threats, key = { it.packageName }) { t ->
                ThreatCard(
                    threat = t,
                    expanded = expanded == t.packageName,
                    onToggle = {
                        expanded = if (expanded == t.packageName) null else t.packageName
                    },
                    onUninstall = {
                        ctx.startActivity(
                            Intent(
                                Intent.ACTION_DELETE,
                                Uri.parse("package:${t.packageName}")
                            )
                        )
                    },
                    onAppInfo = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:${t.packageName}"))
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ThreatCard(
    threat: Threat,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUninstall: () -> Unit,
    onAppInfo: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val color = when (threat.severity) {
        Severity.MALWARE -> cs.error
        Severity.HIGH -> Color(0xFFE65100)
        Severity.MEDIUM -> Color(0xFFF57F17)
        Severity.LOW -> Color(0xFF9E9D24)
        Severity.SAFE -> Color(0xFF2E7D32)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(color, RoundedCornerShape(50))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(threat.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${threat.severity.label} • ${threat.packageName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,
                        null
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                threat.reasons.forEach { r ->
                    Text(
                        "• $r",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAppInfo,
                        modifier = Modifier.weight(1f)
                    ) { Text("اطلاعات") }

                    Button(
                        onClick = onUninstall,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.error
                        ),
                        modifier = Modifier.weight(1f),
                        enabled = !threat.isSystem
                    ) {
                        Text(if (threat.isSystem) "سیستمی" else "حذف اپ")
                    }
                }
            }
        }
    }
}
