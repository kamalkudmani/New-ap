package com.revive.recovery.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.revive.recovery.ui.AppViewModel
import com.revive.recovery.ui.Navigator
import com.revive.recovery.ui.components.iosSpring
import com.revive.recovery.ui.theme.IOS
import com.revive.recovery.ui.theme.IOSType

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Scan", Icons.Rounded.Radar),
    Tab("Recovered", Icons.Rounded.PhotoLibrary),
    Tab("Settings", Icons.Rounded.Settings),
)

val TabBarHeight = 50.dp

@Composable
fun MainTabs(vm: AppViewModel, nav: Navigator, onStartScan: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "tabs") { t ->
            when (t) {
                0 -> HomeScreen(vm, nav, onStartScan)
                1 -> RecoveredScreen(vm, nav)
                else -> SettingsScreen(vm, nav)
            }
        }
        TabBar(tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = IOS.colors
    val haptic = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth().background(c.bar)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(TabBarHeight)) {
            tabs.forEachIndexed { i, t ->
                val active = i == selected
                val s by animateFloatAsState(if (active) 1f else 0.92f, iosSpring, label = "tab$i")
                Column(
                    Modifier.weight(1f).fillMaxSize()
                        .clickable(remember { MutableInteractionSource() }, null) {
                            if (!active) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        }
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val tint = if (active) c.blue else c.secondaryLabel.copy(alpha = 0.55f).let { if (c.dark) it else androidx.compose.ui.graphics.Color(0xFF999999) }
                    Icon(t.icon, t.title, tint = tint, modifier = Modifier.size(26.dp).scale(s))
                    Text(t.title, style = IOSType.caption2, color = tint)
                }
            }
        }
    }
}
