package com.revive.recovery.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.revive.recovery.data.Source
import com.revive.recovery.ui.AppViewModel
import com.revive.recovery.ui.Navigator
import com.revive.recovery.ui.Route
import com.revive.recovery.ui.components.GroupRow
import com.revive.recovery.ui.components.IOSSwitch
import com.revive.recovery.ui.components.InsetGroup
import com.revive.recovery.ui.components.LargeTitle
import com.revive.recovery.ui.components.NavBar
import com.revive.recovery.ui.components.NavBarSpacer
import com.revive.recovery.ui.components.pressable
import com.revive.recovery.ui.theme.IOS
import com.revive.recovery.ui.theme.IOSType

@Composable
fun HomeScreen(vm: AppViewModel, nav: Navigator, onStartScan: () -> Unit) {
    val c = IOS.colors
    val context = LocalContext.current
    val list = rememberLazyListState()
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 1 || (list.firstVisibleItemIndex == 1 && list.firstVisibleItemScrollOffset > 30) } }
    val o = vm.options

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item { NavBarSpacer() }
            item { LargeTitle("Revive", subtitle = "Photo Recovery") }
            if (!vm.hasAccess) item { AccessBanner(onGrant = { openAccessSettings(context) }) }
            item { HeroCard(vm, onStartScan) }
            if (vm.results.isNotEmpty()) item {
                InsetGroup(header = "Last Scan") {
                    GroupRow(
                        "${vm.results.size} photos found", icon = Icons.Rounded.PhotoLibrary, iconTint = c.blue,
                        subtitle = "${vm.scannedCount} files checked · " +
                            DateUtils.getRelativeTimeSpanString(vm.lastScanAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                        chevron = true, showDivider = false, onClick = { nav.push(Route.Results) },
                    )
                }
            }
            item {
                InsetGroup(header = "Scan Locations", footer = "Turn on Gallery in Settings to also list photos that still exist.") {
                    GroupRow(Source.TRASH.title, Icons.Rounded.DeleteSweep, Source.TRASH.color, Source.TRASH.subtitle) {
                        IOSSwitch(o.trash, { v -> vm.updateOptions { it.copy(trash = v) } })
                    }
                    GroupRow(Source.THUMBNAIL.title, Icons.Rounded.PhotoLibrary, Source.THUMBNAIL.color, Source.THUMBNAIL.subtitle) {
                        IOSSwitch(o.thumbnails, { v -> vm.updateOptions { it.copy(thumbnails = v) } })
                    }
                    GroupRow(Source.HIDDEN.title, Icons.Rounded.FolderOff, Source.HIDDEN.color, Source.HIDDEN.subtitle) {
                        IOSSwitch(o.hidden, { v -> vm.updateOptions { it.copy(hidden = v) } })
                    }
                    GroupRow(Source.APP.title, Icons.Rounded.Apps, Source.APP.color, Source.APP.subtitle) {
                        IOSSwitch(o.apps, { v -> vm.updateOptions { it.copy(apps = v) } })
                    }
                    GroupRow(Source.CARVED.title, Icons.Rounded.Extension, Source.CARVED.color, Source.CARVED.subtitle, showDivider = false) {
                        IOSSwitch(o.carve, { v -> vm.updateOptions { it.copy(carve = v) } })
                    }
                }
            }
            item {
                InsetGroup(header = "Tips") {
                    GroupRow(
                        "How recovery works", icon = Icons.Rounded.AutoAwesome, iconTint = c.indigo,
                        subtitle = "What can be found, and why to scan soon", chevron = true, showDivider = false,
                        onClick = { nav.push(Route.HowItWorks) },
                    )
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(TabBarHeight + 32.dp)) }
        }
        NavBar("Revive", collapsed)
    }
}

fun openAccessSettings(context: android.content.Context) {
    val intent = if (Build.VERSION.SDK_INT >= 30)
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
}

@Composable
private fun AccessBanner(onGrant: () -> Unit) {
    val c = IOS.colors
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(c.orange.copy(alpha = 0.14f))
            .pressable(scaleTo = 0.98f, onClick = onGrant).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, null, tint = c.orange, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Allow Full Storage Access", style = IOSType.headline, color = c.label)
            Text("Needed to look inside hidden folders, caches and the trash.", style = IOSType.footnote, color = c.secondaryLabel)
        }
        Text("Allow", style = IOSType.headline, color = c.blue)
    }
}

/** Animated gradient card with a breathing storage ring and the primary call to action. */
@Composable
private fun HeroCard(vm: AppViewModel, onStartScan: () -> Unit) {
    val context = LocalContext.current
    val t = rememberInfiniteTransition(label = "hero")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    val breathe by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "breathe")
    val used = if (vm.storageTotal > 0) vm.storageUsed.toFloat() / vm.storageTotal else 0f

    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFBF5AF2)),
                    start = Offset(0f, 1000f * shift), end = Offset(1200f, 1000f * (1 - shift)),
                )
            )
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Deep Scan", style = IOSType.title1, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Search trash, thumbnail caches, hidden folders and data fragments for lost photos.",
                    style = IOSType.subhead, color = Color.White.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(86.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 9.dp.toPx()
                    drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, style = Stroke(sw))
                    drawArc(Color.White, -90f, 360f * used, false, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(used * 100).toInt()}%", style = IOSType.headline, color = Color.White)
                    Text("used", style = IOSType.caption2, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${Formatter.formatShortFileSize(context, vm.storageUsed)} of ${Formatter.formatShortFileSize(context, vm.storageTotal)}",
            style = IOSType.footnote, color = Color.White.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().height(52.dp)
                .pressable(scaleTo = 0.97f, haptic = true, onClick = onStartScan)
                .clip(RoundedCornerShape(14.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Color(0xFF5E5CE6), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start Scan", style = IOSType.headline, color = Color(0xFF5E5CE6))
            }
        }
    }
}

