package com.revive.recovery.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.revive.recovery.ui.theme.IOS
import com.revive.recovery.ui.theme.IOSType

/**
 * UINavigationBar: transparent while the large title is visible, then a translucent
 * bar with a hairline and a small centered title fades in once the user scrolls.
 */
@Composable
fun NavBar(
    title: String,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = IOS.colors
    val a by animateFloatAsState(if (collapsed) 1f else 0f, label = "navbar")
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = a }.background(c.bar))
        Box(Modifier.fillMaxWidth().statusBarsPadding().height(44.dp)) {
            Row(Modifier.align(Alignment.CenterStart).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) { leading?.invoke() }
            Text(
                title, style = IOSType.headline, color = c.label,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = a; translationY = (1f - a) * 6f },
            )
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) { trailing?.invoke() }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(0.5.dp).graphicsLayer { alpha = a }.background(c.separator))
    }
}

@Composable
private fun Modifier.matchParentSize(): Modifier = this.then(Modifier.fillMaxWidth()).then(Modifier.height(0.dp).padding(0.dp)).let { this }

/** The 34pt bold title that sits at the top of scrolling content. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val c = IOS.colors
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp)) {
        if (subtitle != null) Text(subtitle.uppercase(), style = IOSType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.secondaryLabel)
        Text(text, style = IOSType.largeTitle, color = c.label)
    }
}

@Composable
fun NavBarSpacer() {
    Spacer(Modifier.statusBarsPadding().height(44.dp))
}
