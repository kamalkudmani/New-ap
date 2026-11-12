package com.revive.recovery.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revive.recovery.ui.AppViewModel
import com.revive.recovery.ui.StorageAccess
import com.revive.recovery.ui.components.FilledButton
import com.revive.recovery.ui.components.PlainButton
import com.revive.recovery.ui.theme.IOS
import com.revive.recovery.ui.theme.IOSType
import kotlinx.coroutines.delay

private data class Feature(val icon: ImageVector, val tint: Color, val title: String, val body: String)

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val c = IOS.colors
    val context = LocalContext.current
    val features = listOf(
        Feature(Icons.Rounded.DeleteSweep, c.red, "Recently Deleted", "Pulls photos back out of the system trash before they expire."),
        Feature(Icons.Rounded.PhotoLibrary, c.orange, "Thumbnails & Caches", "Rebuilds previews that apps and the gallery leave behind."),
        Feature(Icons.Rounded.AutoAwesome, c.purple, "Hidden & Renamed", "Finds images in hidden folders, even with the wrong extension."),
        Feature(Icons.Rounded.Lock, c.green, "Private by Design", "Everything stays on your phone. Nothing is uploaded."),
    )

    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshAccess(context); vm.completeOnboarding()
    }
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshAccess(context); vm.completeOnboarding()
    }

    fun requestAccess() {
        if (StorageAccess.hasFullAccess(context)) { vm.completeOnboarding(); return }
        if (Build.VERSION.SDK_INT >= 30) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            runCatching { allFilesLauncher.launch(intent) }.onFailure {
                allFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyLauncher.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(56.dp))
                AppGlyph(size = 96)
                Spacer(Modifier.height(24.dp))
                Text("Welcome to\nRevive", style = IOSType.largeTitle.copy(textAlign = TextAlign.Center), color = c.label)
                Spacer(Modifier.height(40.dp))
                features.forEachIndexed { i, f -> StaggeredFeature(f, i) }
            }
            Text(
                "Revive needs All Files Access to look inside hidden folders, caches and the trash. You can change this anytime in Settings.",
                style = IOSType.footnote.copy(textAlign = TextAlign.Center), color = c.secondaryLabel,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            FilledButton("Continue", onClick = { requestAccess() })
            PlainButton("Not Now", onClick = { vm.completeOnboarding() }, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun StaggeredFeature(f: Feature, index: Int) {
    val c = IOS.colors
    val start = com.revive.recovery.ui.components.enterStart()
    val anim = remember { Animatable(start) }
    LaunchedEffect(Unit) { delay(250L + index * 110L); anim.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f)) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).graphicsLayer {
            alpha = anim.value; translationY = (1f - anim.value) * 40f
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(f.icon, null, tint = f.tint, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text(f.title, style = IOSType.headline, color = c.label)
            Text(f.body, style = IOSType.subhead, color = c.secondaryLabel)
        }
    }
}

/** The app icon drawn in Compose with a slow breathing glow. */
@Composable
fun AppGlyph(size: Int) {
    val t = rememberInfiniteTransition(label = "glyph")
    val glow by t.animateFloat(0.85f, 1.05f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow")
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size((size * 1.5f).dp).graphicsLayer { scaleX = glow; scaleY = glow; alpha = 0.35f }) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF5E5CE6), Color.Transparent)))
        }
        Box(
            Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.225f).dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFBF5AF2)), Offset.Zero, Offset.Infinite)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Restore, null, tint = Color.White, modifier = Modifier.size((size * 0.56f).dp))
        }
    }
}

