package com.example.mobileagent.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * ریشه‌ی اپ — بین صفحه‌ی خوش‌آمد و چت جابجا می‌شه.
 */
@Composable
fun MobileAgentApp(vm: ChatViewModel = viewModel()) {

    var showWelcome by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    AnimatedContent(
        targetState = showWelcome,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith
            fadeOut(animationSpec = tween(400))
        },
        label = "screen"
    ) { welcome ->

        if (welcome) {
            WelcomeScreen(
                onQuickCommand = { command ->
                    // وقتی کاربر روی یه دکمه می‌زنه:
                    // ۱. صفحه‌ی چت باز شه
                    showWelcome = false
                    // ۲. اگه دستور آماده بود، خودکار بفرست
                    // (اگه دستور نیمه‌کاره بود مثل «زنگ بزن به »، کاربر تکمیل کنه)
                    if (!command.endsWith(" ")) {
                        vm.send(command)
                    }
                }
            )
        } else {
            ChatScreen(
                vm = vm,
                onBackToWelcome = { showWelcome = true }
            )
        }
    }
}
