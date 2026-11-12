package com.revive.recovery.ui.screens

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revive.recovery.data.FoundImage
import com.revive.recovery.data.Source
import com.revive.recovery.ui.AppViewModel
import com.revive.recovery.ui.Navigator
import com.revive.recovery.ui.Route
import com.revive.recovery.ui.components.ActivityIndicator
import com.revive.recovery.ui.components.BackButton
import com.revive.recovery.ui.components.FilledButton
import com.revive.recovery.ui.components.LargeTitle
import com.revive.recovery.ui.components.NavBar
import com.revive.recovery.ui.components.NavBarSpacer
import com.revive.recovery.ui.components.PhotoImage
import com.revive.recovery.ui.components.PlainButton
import com.revive.recovery.ui.components.SegmentedControl
import com.revive.recovery.ui.components.SelectionBadge
import com.revive.recovery.ui.components.iosSpring
import com.revive.recovery.ui.components.pressable
import com.revive.recovery.ui.theme.IOS
import com.revive.recovery.ui.theme.IOSType

@Composable
fun ResultsScreen(vm: AppViewModel, nav: Navigator) {
    val c = IOS.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var columns by rememberSaveable { mutableIntStateOf(3) }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<String>()) }

    val source = filter?.let { Source.valueOf(it) }
    val items by remember(source, sort) {
        derivedStateOf {
            val base = if (source == null) vm.results.toList() else vm.results.filter { it.source == source }
            when (sort) { 0 -> base.sortedByDescending { it.modified }; 1 -> base.sortedByDescending { it.sizeBytes }; else -> base.sortedByDescending { it.width.toLong() * it.height } }
        }
    }
    val grid = rememberLazyGridState()
    val collapsed by remember { derivedStateOf { grid.firstVisibleItemIndex > 1 } }
    val totalBytes = remember(items) { items.sumOf { it.sizeBytes } }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize().pinchToResize(
                onZoomIn = { if (columns > 2) { columns--; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } },
                onZoomOut = { if (columns < 6) { columns++; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } },
            ),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { NavBarSpacer() }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LargeTitle(
                    "${items.size} Photos",
                    subtitle = if (items.isEmpty()) "Results" else Formatter.formatShortFileSize(context, totalBytes) + " recoverable",
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SourceChips(vm, filter) { filter = it }
                    SegmentedControl(
                        listOf("Newest", "Largest", "Sharpest"), sort, { sort = it },
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            if (items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyResults() }
            items(items, key = { it.id }) { img ->
                val isSel = img.id in selected.value
                PhotoTile(
                    img, selecting, isSel, showSource = source == null,
                    onClick = {
                        if (selecting) selected.value = selected.value.toggle(img.id)
                        else nav.push(Route.Viewer(items, items.indexOf(img), recoverable = true))
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selecting = true; selected.value = selected.value + img.id
                    },
                )
            }
        }

        NavBar(
            "${items.size} Photos", collapsed,
            leading = {
                if (selecting) PlainButton(if (selected.value.size == items.size && items.isNotEmpty()) "Deselect All" else "Select All") {
                    selected.value = if (selected.value.size == items.size) emptySet() else items.map { it.id }.toSet()
                } else BackButton("Scan") { nav.pop() }
            },
            trailing = {
                if (items.isNotEmpty()) {
                    Box(
                        Modifier.clip(CircleShape).background(if (selecting) c.blue else c.fill)
                            .pressable(scaleTo = 0.94f) { selecting = !selecting; if (!selecting) selected.value = emptySet() }
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) { Text(if (selecting) "Cancel" else "Select", style = IOSType.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (selecting) Color.White else c.blue) }
                }
            },
        )

        // Bottom recover toolbar
        AnimatedVisibility(
            visible = !selecting && items.isNotEmpty() || selecting && selected.value.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth().background(c.bar).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                val toRestore = if (selecting) items.filter { it.id in selected.value } else items
                if (vm.restoring) {
                    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        ActivityIndicator(); Spacer(Modifier.width(10.dp))
                        Text("Recovering…", style = IOSType.headline, color = c.secondaryLabel)
                    }
                } else {
                    FilledButton(
                        if (selecting) "Recover ${toRestore.size} Selected" else "Recover All ${toRestore.size}",
                        icon = Icons.Rounded.Restore,
                    ) { vm.restore(toRestore) { selecting = false; selected.value = emptySet() } }
                }
            }
        }
    }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

/** Two-finger pinch changes the grid density like the Photos app; single-finger scrolling is untouched. */
private fun Modifier.pinchToResize(onZoomIn: () -> Unit, onZoomOut: () -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                if (zoom > 1.35f) { onZoomIn(); zoom = 1f } else if (zoom < 0.74f) { onZoomOut(); zoom = 1f }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun SourceChips(vm: AppViewModel, filter: String?, onSelect: (String?) -> Unit) {
    val c = IOS.colors
    val entries = listOf<Source?>(null) + Source.entries.filter { vm.countFor(it) > 0 }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries.size) { i ->
            val s = entries[i]
            val active = filter == s?.name
            val scale by animateFloatAsState(if (active) 1f else 0.97f, iosSpring, label = "chip")
            Row(
                Modifier.scale(scale).clip(CircleShape)
                    .background(if (active) (s?.color ?: c.blue) else c.card)
                    .pressable(scaleTo = 0.94f) { onSelect(s?.name) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (s != null && !active) { Box(Modifier.size(8.dp).clip(CircleShape).background(s.color)); Spacer(Modifier.width(6.dp)) }
                Text(s?.title ?: "All", style = IOSType.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (active) Color.White else c.label)
                Spacer(Modifier.width(6.dp))
                Text("${vm.countFor(s)}", style = IOSType.subhead, color = if (active) Color.White.copy(alpha = 0.8f) else c.secondaryLabel)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoTile(
    img: FoundImage, selecting: Boolean, selected: Boolean, showSource: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val c = IOS.colors
    val scale by animateFloatAsState(if (selected) 0.92f else 1f, iosSpring, label = "tile")
    Box(
        Modifier.aspectRatio(1f).background(c.fill)
            .combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = onLongClick, onClick = onClick)
    ) {
        PhotoImage(img.file, Modifier.fillMaxSize().scale(scale), sizePx = 360)
        if (showSource) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(5.dp).size(8.dp).clip(CircleShape)
                    .background(img.source.color)
            )
        }
        if (selecting) SelectionBadge(selected, Modifier.align(Alignment.BottomEnd).padding(5.dp))
    }
}

@Composable
private fun EmptyResults() {
    val c = IOS.colors
    Column(Modifier.fillMaxWidth().padding(top = 80.dp, start = 40.dp, end = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.PhotoLibrary, null, tint = c.tertiaryLabel, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(14.dp))
        Text("No Photos Found", style = IOSType.title3, color = c.label)
        Spacer(Modifier.height(4.dp))
        Text("Try enabling more scan locations, lowering the minimum size, or a root deep scan.", style = IOSType.subhead, color = c.secondaryLabel,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

