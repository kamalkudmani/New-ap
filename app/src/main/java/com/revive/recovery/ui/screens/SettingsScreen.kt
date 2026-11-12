package com.revive.recovery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.revive.recovery.BuildConfigCompat
import com.revive.recovery.data.Restorer
import com.revive.recovery.ui.AppViewModel
import com.revive.recovery.ui.Navigator
import com.revive.recovery.ui.Route
import com.revive.recovery.ui.components.BackButton
import com.revive.recovery.ui.components.GroupRow
import com.revive.recovery.ui.components.IOSSwitch
import com.revive.recovery.ui.components.InsetGroup
import com.revive.recovery.ui.components.LargeTitle
import com.revive.recovery.ui.components.NavBar
import com.revive.recovery.ui.components.NavBarSpacer
import com.revive.recovery.ui.components.SegmentedControl
import com.revive.recovery.ui.theme.IOS
import com.revive.recovery.ui.theme.IOSType

private val sizeSteps = listOf(1, 4, 16, 64)

@Composable
fun SettingsScreen(vm: AppViewModel, nav: Navigator) {
    val c = IOS.colors
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val collapsed by remember { derivedStateOf { scroll.value > 120 } }
    val o = vm.options

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            NavBarSpacer()
            LargeTitle("Settings")

            InsetGroup(header = "Access") {
                GroupRow(
                    "All Files Access", icon = Icons.Rounded.Folder, iconTint = if (vm.hasAccess) c.green else c.orange,
                    value = if (vm.hasAccess) "On" else "Off", chevron = true, showDivider = false,
                    onClick = { openAccessSettings(context) },
                )
            }

            InsetGroup(
                header = "Advanced Scanning",
                footer = "Deep Scan uses root (su) to copy cached images out of app-private storage. It is only available on rooted devices.",
            ) {
                GroupRow("Deep Scan (Root)", Icons.Rounded.Memory, c.teal, if (vm.rootAvailable == false) "Root not detected" else "Reads app caches under /data") {
                    IOSSwitch(o.root, { vm.setRootEnabled(it) })
                }
                GroupRow("Include Gallery Photos", Icons.Rounded.PhotoLibrary, c.blue, "Also list photos that were never deleted", showDivider = false) {
                    IOSSwitch(o.gallery, { v -> vm.updateOptions { it.copy(gallery = v) } })
                }
            }

            InsetGroup(header = "Minimum Image Size", footer = "Smaller sizes find more tiny thumbnails, but also icons and stickers.") {
                Column(Modifier.padding(12.dp)) {
                    SegmentedControl(
                        sizeSteps.map { "$it KB" },
                        sizeSteps.indexOf(o.minSizeKb).coerceAtLeast(0),
                        { i -> vm.updateOptions { it.copy(minSizeKb = sizeSteps[i]) } },
                    )
                }
            }

            InsetGroup(header = "About") {
                GroupRow("How Recovery Works", icon = Icons.Rounded.AutoAwesome, iconTint = c.indigo, chevron = true, onClick = { nav.push(Route.HowItWorks) })
                GroupRow("Saved To", icon = Icons.Rounded.Straighten, iconTint = c.pink, value = "Pictures/${Restorer.ALBUM}")
                GroupRow("Privacy", icon = Icons.Rounded.CheckCircle, iconTint = c.green, value = "On-device only")
                GroupRow("Version", icon = Icons.Rounded.Info, iconTint = c.secondaryLabel, value = BuildConfigCompat.versionName(context), showDivider = false)
            }
            Spacer(Modifier.navigationBarsPadding().height(TabBarHeight + 40.dp))
        }
        NavBar("Settings", collapsed)
    }
}

@Composable
fun HowItWorksScreen(nav: Navigator) {
    val c = IOS.colors
    val scroll = rememberScrollState()
    val collapsed by remember { derivedStateOf { scroll.value > 120 } }
    val sections = listOf(
        "Recently Deleted" to "On Android 11 and later, deleted photos usually sit in the system trash for up to 30 days. Revive reads those trashed files and copies them back out.",
        "Thumbnail Cache" to "Gallery apps and the system create small previews of every photo. These often survive after the original is deleted. Revive finds them and also rebuilds previews packed inside cache databases.",
        "Hidden & Disguised" to "Photos inside hidden folders, .nomedia folders, or files renamed with the wrong extension are detected by reading their actual bytes, not their names.",
        "App Media" to "Messaging and social apps keep their own copies of photos you sent or received, even after you delete them from your gallery.",
        "Fragments" to "Cache files and databases are searched byte-by-byte for embedded JPEG and PNG images, which are then extracted as standalone pictures.",
        "Deep Scan (Root)" to "On rooted phones, app-private caches under /data are also searched. Without root, Android does not allow any app to read them.",
        "What can't be recovered" to "Modern Android encrypts storage and immediately frees the space of deleted files, so a photo that was deleted, emptied from trash, and has no thumbnail or app copy left usually cannot be recovered by any app. Scan as soon as possible and avoid filling the phone with new data first.",
    )
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            NavBarSpacer()
            LargeTitle("How It Works")
            sections.forEach { (title, body) ->
                InsetGroup(header = title) {
                    Text(body, style = IOSType.body, color = c.label, modifier = Modifier.padding(16.dp))
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(40.dp))
        }
        NavBar("How It Works", collapsed, leading = { BackButton("Back") { nav.pop() } })
    }
}
