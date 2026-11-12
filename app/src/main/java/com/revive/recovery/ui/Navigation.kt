package com.revive.recovery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.revive.recovery.data.FoundImage
import com.revive.recovery.ui.components.HudOverlay
import com.revive.recovery.ui.screens.HowItWorksScreen
import com.revive.recovery.ui.screens.MainTabs
import com.revive.recovery.ui.screens.OnboardingScreen
import com.revive.recovery.ui.screens.ResultsScreen
import com.revive.recovery.ui.screens.ScanningScreen
import com.revive.recovery.ui.screens.ViewerScreen
import com.revive.recovery.ui.theme.IOS

sealed interface Route {
    data object Home : Route
    data object Results : Route
    data object HowItWorks : Route
    data class Viewer(val images: List<FoundImage>, val start: Int, val recoverable: Boolean) : Route
}

class Navigator {
    val stack = mutableStateListOf<Route>(Route.Home)
    var popping by mutableStateOf(false); private set
    val top: Route get() = stack.last()
    fun push(r: Route) { popping = false; stack.add(r) }
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        popping = true; stack.removeAt(stack.lastIndex); return true
    }
}

private val iosEase = FastOutSlowInEasing
private const val PUSH_MS = 380

private fun pushTransition(popping: Boolean, target: Route, initial: Route): ContentTransform {
    // Viewer zooms/fades like the Photos app; everything else uses the UINavigationController slide.
    if (target is Route.Viewer || initial is Route.Viewer) {
        return if (!popping) (fadeIn(tween(220)) + scaleIn(tween(320, easing = iosEase), initialScale = 0.88f)) togetherWith fadeOut(tween(260))
        else fadeIn(tween(200)) togetherWith (fadeOut(tween(220)) + scaleOut(tween(260), targetScale = 0.9f))
    }
    val t = if (!popping) {
        slideInHorizontally(tween(PUSH_MS, easing = iosEase)) { it } togetherWith
            slideOutHorizontally(tween(PUSH_MS, easing = iosEase)) { -it / 3 } + fadeOut(tween(PUSH_MS), 0.6f)
    } else {
        (slideInHorizontally(tween(PUSH_MS, easing = iosEase)) { -it / 3 } + fadeIn(tween(PUSH_MS), 0.6f)) togetherWith
            slideOutHorizontally(tween(PUSH_MS, easing = iosEase)) { it }
    }
    t.targetContentZIndex = if (popping) -1f else 1f
    return t
}

@Composable
fun ReviveRoot(vm: AppViewModel) {
    val nav = remember { Navigator() }
    var showScan by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(IOS.colors.background)) {
        AnimatedContent(
            targetState = vm.onboarded,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
            label = "onboarding",
        ) { onboarded ->
            if (!onboarded) {
                OnboardingScreen(vm)
            } else {
                AnimatedContent(
                    targetState = nav.top,
                    transitionSpec = { pushTransition(nav.popping, targetState, initialState) },
                    label = "nav",
                ) { route ->
                    when (route) {
                        Route.Home -> MainTabs(vm, nav, onStartScan = { vm.startScan(); showScan = true })
                        Route.Results -> ResultsScreen(vm, nav)
                        Route.HowItWorks -> HowItWorksScreen(nav)
                        is Route.Viewer -> ViewerScreen(vm, nav, route)
                    }
                }
            }
        }

        // Full-screen scanning sheet slides up over everything, like a modal presentation.
        AnimatedVisibility(
            visible = showScan,
            enter = slideInVertically(tween(480, easing = iosEase)) { it } + fadeIn(),
            exit = slideOutVertically(tween(420, easing = iosEase)) { it } + fadeOut(),
        ) {
            ScanningScreen(
                vm,
                onClose = { showScan = false; vm.dismissScan() },
                onReview = {
                    showScan = false; vm.dismissScan()
                    if (nav.top != Route.Results) nav.push(Route.Results)
                },
            )
        }

        HudOverlay(visible = vm.hud != null, text = vm.hud ?: "")
    }

    BackHandler(enabled = showScan) {
        if (vm.status == ScanStatus.Running) vm.stopScan() else { showScan = false; vm.dismissScan() }
    }
    BackHandler(enabled = !showScan && nav.stack.size > 1) { nav.pop() }
}
