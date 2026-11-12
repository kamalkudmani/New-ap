package com.revive.recovery.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revive.recovery.data.FoundImage
import com.revive.recovery.data.Source
import com.revive.recovery.ui.AppViewModel
import com.revive.recovery.ui.ScanStatus
import com.revive.recovery.ui.components.PhotoImage
import com.revive.recovery.ui.components.enterStart
import com.revive.recovery.ui.components.pressable
import com.revive.recovery.ui.theme.IOSType
import kotlin.math.cos
import kotlin.math.sin

private val Night = listOf(Color(0xFF05060F), Color(0xFF0D1030), Color(0xFF1A0F33))
private val Accent = Color(0xFF64D2FF)

@Composable
fun ScanningScreen(vm: AppViewModel, onClose: () -> Unit, onReview: () -> Unit) {
    val running = vm.status == ScanStatus.Running
    val done = vm.status == ScanStatus.Finished || vm.status == ScanStatus.Cancelled
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(done) { if (done) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    val t = rememberInfiniteTransition(label = "bg")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(Night))
    ) {
        // Soft aurora blobs drifting behind everything
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0x665E5CE6), Color.Transparent), center = Offset(size.width * (0.2f + 0.5f * drift), size.height * 0.25f), radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = Offset(size.width * (0.2f + 0.5f * drift), size.height * 0.25f),
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0x4430B0C7), Color.Transparent), center = Offset(size.width * (0.9f - 0.6f * drift), size.height * 0.7f), radius = size.width * 0.7f),
                radius = size.width * 0.7f, center = Offset(size.width * (0.9f - 0.6f * drift), size.height * 0.7f),
            )
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (!running) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                            .pressable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, "Close", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp)) }
                }
            }

            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = if (done) "Scan Complete" else vm.phase.ifEmpty { "Preparing" },
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                }, label = "phase",
            ) { Text(it, style = IOSType.title2, color = Color.White, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(6.dp))
            Text(
                if (done) "${vm.scannedCount} files checked in ${formatDuration(vm.elapsedMs)}" else vm.currentPath.let { if (it.length > 46) "…" + it.takeLast(45) else it.ifEmpty { " " } },
                style = IOSType.footnote.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Radar(vm, running, done)
            }

            SourceBreakdown(vm)
            Spacer(Modifier.height(18.dp))

            if (running) {
                PillButton("Stop Scan", Color.White.copy(alpha = 0.14f), Color.White) { vm.stopScan() }
            } else {
                AnimatedVisibility(done, enter = slideInVertically { it } + fadeIn()) {
                    val n = vm.results.size
                    PillButton(
                        if (n == 0) "Done" else "Review $n Photo${if (n == 1) "" else "s"}",
                        Color.White, Color(0xFF1A0F33),
                    ) { if (n == 0) onClose() else onReview() }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Radar(vm: AppViewModel, running: Boolean, done: Boolean) {
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "pulse")
    val orbit by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "orbit")
    val progress by animateFloatAsState(vm.progress, tween(600, easing = FastOutSlowInEasing), label = "progress")
    val count by animateIntAsState(vm.results.size, tween(500), label = "count")
    val start = enterStart()
    val doneScale = remember { Animatable(start) }
    LaunchedEffect(done) { if (done) doneScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f)) else doneScale.snapTo(start) }

    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(260.dp)) {
            val r = size.minDimension / 2
            // pulse rings
            if (running) for (k in 0 until 3) {
                val p = (pulse + k / 3f) % 1f
                drawCircle(Accent.copy(alpha = (1f - p) * 0.45f), radius = r * (0.35f + 0.75f * p), style = Stroke(1.5.dp.toPx()))
            }
            // grid rings
            for (k in 1..3) drawCircle(Color.White.copy(alpha = 0.08f), radius = r * k / 3f, style = Stroke(1.dp.toPx()))
            // radar sweep
            if (running) rotate(sweep) {
                drawCircle(
                    Brush.sweepGradient(
                        0f to Color.Transparent, 0.75f to Color.Transparent, 1f to Accent.copy(alpha = 0.55f),
                        center = center,
                    ),
                    radius = r,
                )
                drawLine(Accent, center, Offset(center.x + r, center.y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            // progress arc
            val sw = 6.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.10f), 0f, 360f, false, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw))
            drawArc(
                Brush.sweepGradient(listOf(Accent, Color(0xFF5E5CE6), Color(0xFFBF5AF2), Accent)),
                -90f, 360f * progress, false, topLeft = Offset(sw / 2, sw / 2),
                size = Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round),
            )
        }

        // Thumbnails of the latest finds orbit the radar
        val recent = vm.results.takeLast(6)
        recent.forEachIndexed { i, img ->
            key(img.id) { OrbitThumb(img, angle = orbit + i * 60f, radius = 138f) }
        }

        // Center readout
        Box(
            Modifier.size(150.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF1C2050), Color(0xFF10132E))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Box(
                    Modifier.size(84.dp).graphicsLayer { scaleX = doneScale.value; scaleY = doneScale.value }
                        .clip(CircleShape).background(Color(0xFF30D158)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", style = IOSType.largeTitle.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold), color = Color.White)
                    Text("photos found", style = IOSType.footnote, color = Color.White.copy(alpha = 0.6f))
                    Text("${(progress * 100).toInt()}%", style = IOSType.caption2, color = Accent, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (done) {
            Text(
                "$count found", style = IOSType.headline, color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 44.dp).graphicsLayer { alpha = doneScale.value.coerceIn(0f, 1f) },
            )
        }
    }
}

@Composable
private fun OrbitThumb(img: FoundImage, angle: Float, radius: Float) {
    val start = enterStart()
    val appear = remember { Animatable(start) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
    val rad = Math.toRadians(angle.toDouble())
    Box(
        Modifier
            .offset(x = (radius * cos(rad)).dp, y = (radius * sin(rad)).dp)
            .size(42.dp)
            .graphicsLayer { scaleX = appear.value; scaleY = appear.value; alpha = appear.value.coerceIn(0f, 1f) }
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.1f))
    ) {
        PhotoImage(img.file, Modifier.fillMaxSize(), sizePx = 160)
    }
}

@Composable
private fun SourceBreakdown(vm: AppViewModel) {
    val sources = Source.entries.filter { vm.countFor(it) > 0 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        sources.take(4).forEach { s ->
            key(s) {
                val start = enterStart()
                val appear = remember { Animatable(start) }
                LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f)) }
                Column(
                    Modifier.graphicsLayer { scaleX = appear.value; scaleY = appear.value }
                        .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${vm.countFor(s)}", style = IOSType.headline, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(s.color))
                        Spacer(Modifier.width(4.dp))
                        Text(s.short, style = IOSType.caption2, color = Color.White.copy(alpha = 0.65f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PillButton(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp).pressable(scaleTo = 0.96f, haptic = true, onClick = onClick)
            .clip(RoundedCornerShape(27.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = IOSType.headline, color = fg) }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}

