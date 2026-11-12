package com.revive.recovery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revive.recovery.data.FoundImage
import com.revive.recovery.data.ImageMagic
import com.revive.recovery.data.Restorer
import com.revive.recovery.data.Source
import com.revive.recovery.ui.AppViewModel
import com.revive.recovery.ui.Navigator
import com.revive.recovery.ui.Route
import com.revive.recovery.ui.components.LargeTitle
import com.revive.recovery.ui.components.NavBar
import com.revive.recovery.ui.components.NavBarSpacer
import com.revive.recovery.ui.theme.IOS
import com.revive.recovery.ui.theme.IOSType

@Composable
fun RecoveredScreen(vm: AppViewModel, nav: Navigator) {
    val c = IOS.colors
    LaunchedEffect(Unit) { vm.refreshRestored() }
    val images = remember(vm.restored.toList()) {
        vm.restored.map { f ->
            FoundImage(
                id = f.absolutePath, file = f, originPath = f.absolutePath, source = Source.GALLERY,
                sizeBytes = f.length(), modified = f.lastModified(), mime = ImageMagic.detect(f) ?: "image/jpeg",
            )
        }
    }
    val grid = rememberLazyGridState()
    val collapsed by remember { derivedStateOf { grid.firstVisibleItemIndex > 1 } }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyVerticalGrid(
            GridCells.Fixed(3), state = grid, modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { NavBarSpacer() }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LargeTitle("Recovered", subtitle = "Pictures/${Restorer.ALBUM} · ${images.size} items")
            }
            if (images.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(top = 120.dp, start = 40.dp, end = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.PhotoLibrary, null, tint = c.tertiaryLabel, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("No Recovered Photos", style = IOSType.title3, color = c.label)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Photos you recover are saved to the Revive album and appear in your gallery.",
                        style = IOSType.subhead, color = c.secondaryLabel, textAlign = TextAlign.Center,
                    )
                }
            }
            itemsIndexed(images, key = { _, it -> it.id }) { i, img ->
                PhotoTile(img, selecting = false, selected = false, showSource = false,
                    onClick = { nav.push(Route.Viewer(images, i, recoverable = false)) }, onLongClick = {})
            }
        }
        NavBar("Recovered", collapsed)
    }
}
