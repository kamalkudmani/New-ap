package com.revive.recovery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.revive.recovery.ui.theme.IOS
import com.revive.recovery.ui.theme.IOSType
import kotlinx.coroutines.delay

val iosSpring = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

/** iOS-style press feedback: content dims and shrinks slightly with a spring. */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    scaleTo: Float = 0.96f,
    haptic: Boolean = false,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleTo else 1f, iosSpring, label = "press")
    val a by animateFloatAsState(if (pressed) 0.7f else 1f, label = "pressAlpha")
    val h = LocalHapticFeedback.current
    return this
        .scale(s)
        .graphicsLayer { alpha = a }
        .clickable(source, indication = null, enabled = enabled) {
            if (haptic) h.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
}


@Composable
fun IOSSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = IOS.colors
    val h = LocalHapticFeedback.current
    val track by animateColorAsState(if (checked) c.green else c.fill.copy(alpha = if (c.dark) 0.5f else 0.16f), label = "track")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.68f, stiffness = 700f), label = "thumb")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val thumbW by animateDpAsState(if (pressed) 33.dp else 27.dp, label = "stretch")
    Box(
        Modifier
            .size(51.dp, 31.dp)
            .clip(CircleShape)
            .background(track)
            .graphicsLayerAlphaIf(!enabled)
            .clickable(source, null, enabled) {
                h.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
    ) {
        Box(
            Modifier
                .offset(x = if (checked && pressed) x - 6.dp else x, y = 2.dp)
                .size(thumbW, 27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

private fun Modifier.graphicsLayerAlphaIf(dim: Boolean) =
    if (dim) this.graphicsLayer { alpha = 0.4f } else this

/** Inset grouped table section (Settings.app style). */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = IOS.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                header.uppercase(), style = IOSType.footnote, color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 7.dp, top = 22.dp),
            )
        } else Spacer(Modifier.height(22.dp))
        Column(Modifier.clip(RoundedCornerShape(12.dp)).background(c.card), content = content)
        if (footer != null) {
            Text(
                footer, style = IOSType.footnote, color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

@Composable
fun IconSquare(icon: ImageVector, tint: Color, size: Dp = 29.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(tint),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.62f)) }
}

@Composable
fun GroupRow(
    title: String,
    icon: ImageVector? = null,
    iconTint: Color = IOS.colors.blue,
    subtitle: String? = null,
    value: String? = null,
    showDivider: Boolean = true,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = IOS.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (pressed) c.fill else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(source, null) { onClick() } else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp).height(if (subtitle != null) 44.dp else 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconSquare(icon, iconTint)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = IOSType.body, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = IOSType.footnote, color = c.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) Text(value, style = IOSType.body, color = c.secondaryLabel, modifier = Modifier.padding(start = 8.dp))
            trailing?.invoke()
            if (chevron) Icon(Icons.Rounded.ChevronRight, null, tint = c.tertiaryLabel, modifier = Modifier.padding(start = 4.dp).size(22.dp))
        }
        if (showDivider) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = if (icon != null) 59.dp else 16.dp)
                    .fillMaxWidth().height(0.5.dp).background(c.separator)
            )
        }
    }
}

/** UISegmentedControl with a sliding, springy thumb. */
@Composable
fun SegmentedControl(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = IOS.colors
    val h = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(9.dp)).background(c.fill).padding(2.dp)
    ) {
        val segW = maxWidth / items.size
        val x by animateDpAsState(segW * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "seg")
        Box(
            Modifier.offset(x = x).width(segW).fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(7.dp))
                .background(if (c.dark) Color(0xFF636366) else Color.White, RoundedCornerShape(7.dp))
        )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) {
                        if (i != selected) h.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(i)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, style = if (i == selected) IOSType.footnote.copy(fontWeight = FontWeight.SemiBold) else IOSType.footnote,
                        color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


@Composable
fun FilledButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = IOS.colors.blue,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressable(enabled = enabled, scaleTo = 0.97f, haptic = true, onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else IOS.colors.fill),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = IOSType.headline, color = if (enabled) Color.White else IOS.colors.tertiaryLabel)
        }
    }
}

@Composable
fun PlainButton(text: String, color: Color = IOS.colors.blue, bold: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        style = if (bold) IOSType.headline else IOSType.body,
        color = color,
        modifier = modifier.pressable(scaleTo = 1f, onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
fun BackButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier.pressable(scaleTo = 1f, onClick = onClick).padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = IOS.colors.blue, modifier = Modifier.size(20.dp))
        Text(label, style = IOSType.body, color = IOS.colors.blue)
    }
}

/** UIActivityIndicatorView: 8 fading spokes. */
@Composable
fun ActivityIndicator(size: Dp = 22.dp, color: Color = IOS.colors.secondaryLabel) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(90); step = (step + 1) % 8 } }
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        for (i in 0 until 8) {
            val age = (step - i + 8) % 8
            val alpha = 1f - age / 8f * 0.85f
            val angle = Math.toRadians((i * 45 - 90).toDouble())
            val cx = center.x; val cy = center.y
            val start = Offset(cx + (r * 0.45f * kotlin.math.cos(angle)).toFloat(), cy + (r * 0.45f * kotlin.math.sin(angle)).toFloat())
            val end = Offset(cx + (r * 0.95f * kotlin.math.cos(angle)).toFloat(), cy + (r * 0.95f * kotlin.math.sin(angle)).toFloat())
            drawLine(color.copy(alpha = alpha), start, end, strokeWidth = r * 0.22f, cap = StrokeCap.Round)
        }
    }
}

/** Selection check circle used on photo tiles. */
@Composable
fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val c = IOS.colors
    val s by animateFloatAsState(if (selected) 1f else 0.85f, iosSpring, label = "badge")
    Box(
        modifier.size(24.dp).scale(s)
            .shadow(if (selected) 2.dp else 0.dp, CircleShape)
            .clip(CircleShape)
            .background(if (selected) c.blue else Color.Black.copy(alpha = 0.18f))
            .then(if (!selected) Modifier.borderCircle() else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

private fun Modifier.borderCircle() = this.border(1.5.dp, Color.White, CircleShape)

/** Translucent square HUD (like the iOS "Saved" confirmation). */
@Composable
fun HudOverlay(visible: Boolean, text: String, icon: ImageVector = Icons.Rounded.Check) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible,
            enter = fadeIn() + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.6f)),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            val c = IOS.colors
            Column(
                Modifier.size(160.dp).clip(RoundedCornerShape(22.dp))
                    .background(if (c.dark) Color(0xEE2C2C2E) else Color(0xEEF2F2F7)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val rot by animateFloatAsState(if (visible) 0f else -30f, iosSpring, label = "hudIcon")
                Icon(icon, null, tint = c.label, modifier = Modifier.size(64.dp).rotate(rot))
                Spacer(Modifier.height(10.dp))
                Text(text, style = IOSType.headline, color = c.label)
            }
        }
    }
}
