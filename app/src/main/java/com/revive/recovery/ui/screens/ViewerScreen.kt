package com.revive.recovery.ui.screens

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.revive.recovery.data.FoundImage
import com.revive.recovery.ui.AppViewModel
import com.revive.recovery.ui.Navigator
import com.revive.recovery.ui.Route
import com.revive.recovery.ui.components.BackButton
import com.revive.recovery.ui.components.PhotoImage
import com.revive.recovery.ui.components.pressable
import com.revive.recovery.ui.theme.IOS
import com.revive.recovery.ui.theme.IOSType
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs

@Composable
fun ViewerScreen(vm: AppViewModel, nav: Navigator, route: Route.Viewer) {
    val c = IOS.colors
    val context = LocalContext.current
    val images = route.images
    if (images.isEmpty()) { nav.pop(); return }
    val pager = rememberPagerState(initialPage = route.start.coerceIn(0, images.lastIndex)) { images.size }
    var chrome by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(false) }
    val dismiss = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dragFrac = (abs(dismiss.value) / 900f).coerceIn(0f, 1f)
    val current = images[pager.currentPage]

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - dragFrac))) {
        HorizontalPager(
            state = pager, beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationY = dismiss.value
                val s = 1f - dragFrac * 0.35f; scaleX = s; scaleY = s
            },
        ) { page ->
            ZoomableImage(
                images[page],
                onTap = { chrome = !chrome },
                onDrag = { dy -> scope.launch { dismiss.snapTo(dismiss.value + dy) } },
                onDragEnd = {
                    scope.launch {
                        if (abs(dismiss.value) > 260f) nav.pop()
                        else dismiss.animateTo(0f, spring(dampingRatio = 0.75f))
                    }
                },
            )
        }

        // Top chrome: date title like Photos.app
        AnimatedVisibility(chrome && dragFrac < 0.05f, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Box(Modifier.fillMaxWidth().background(c.bar).statusBarsPadding().height(52.dp)) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) { BackButton("Back") { nav.pop() } }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(DateFormat.format("d MMMM yyyy", Date(current.modified)).toString(), style = IOSType.headline, color = c.label)
                    Text(DateFormat.format("HH:mm", Date(current.modified)).toString() + " · ${pager.currentPage + 1} of ${images.size}", style = IOSType.caption, color = c.secondaryLabel)
                }
            }
        }

        // Bottom toolbar
        AnimatedVisibility(
            chrome && dragFrac < 0.05f, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth().background(c.bar).navigationBarsPadding()) {
                AnimatedVisibility(info) { InfoPanel(current) }
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Info, "Info", tint = if (info) c.blue else c.label,
                        modifier = Modifier.size(28.dp).pressable(scaleTo = 0.85f) { info = !info },
                    )
                    Spacer(Modifier.weight(1f))
                    if (route.recoverable) {
                        Row(
                            Modifier.clip(CircleShape).background(c.blue)
                                .pressable(scaleTo = 0.94f, haptic = true) { vm.restore(listOf(current)) }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Restore, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Recover", style = IOSType.headline, color = Color.White)
                        }
                    } else {
                        Text(Formatter.formatShortFileSize(context, current.sizeBytes), style = IOSType.subhead, color = c.secondaryLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPanel(img: FoundImage) {
    val c = IOS.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)).background(c.card).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(img.source.color))
            Spacer(Modifier.width(8.dp))
            Text(img.source.title, style = IOSType.headline, color = c.label)
        }
        Spacer(Modifier.height(6.dp))
        Text(img.originPath, style = IOSType.footnote, color = c.secondaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        val dims = if (img.width > 0) "${img.width} × ${img.height}" else "—"
        Text(
            "${img.mime.substringAfter('/').uppercase()}  ·  $dims  ·  ${Formatter.formatShortFileSize(context, img.sizeBytes)}",
            style = IOSType.subhead, color = c.label,
        )
    }
}

/** Pinch / double-tap zoom with pan; when not zoomed, vertical drags drive swipe-to-dismiss. */
@Composable
private fun ZoomableImage(img: FoundImage, onTap: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val animScale by animateFloatAsState(scale, spring(dampingRatio = 0.8f), label = "zoom")

    Box(
        Modifier.fillMaxSize()
            .pointerInput(img.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2.5f },
                )
            }
            .then(
                if (scale > 1f) Modifier.pointerInput(img.id, "zoomed") {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offsetX += pan.x; offsetY += pan.y
                        if (scale <= 1f) { offsetX = 0f; offsetY = 0f }
                    }
                } else Modifier
                    .pointerInput(img.id, "pinch") {
                        // Only react to two fingers so single-finger swipes still page.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val e = awaitPointerEvent()
                                if (e.changes.count { it.pressed } >= 2) {
                                    val z = e.calculateZoom()
                                    if (z != 1f) scale = (scale * z).coerceIn(1f, 6f)
                                    e.changes.forEach { it.consume() }
                                }
                            } while (e.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(img.id, "dismiss") {
                        detectVerticalDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, dy ->
                            change.consume(); onDrag(dy)
                        }
                    }
            ),
        contentAlignment = Alignment.Center,
    ) {
        PhotoImage(
            img.file, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = animScale; scaleY = animScale; translationX = offsetX; translationY = offsetY
            },
        )
    }
}
