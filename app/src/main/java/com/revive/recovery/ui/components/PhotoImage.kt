package com.revive.recovery.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * Loads a recovered image asynchronously with Coil. In previews / screenshot tests
 * (inspection mode) the file is decoded synchronously so the real pixels show up.
 */
@Composable
fun PhotoImage(file: File, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, sizePx: Int? = null) {
    if (LocalInspectionMode.current) {
        val bmp = remember(file) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
        }
        if (bmp != null) Image(bmp, null, modifier, contentScale = contentScale)
        return
    }
    val context = LocalContext.current
    val request = remember(file, sizePx) {
        ImageRequest.Builder(context).data(file).apply { if (sizePx != null) size(sizePx) }.build()
    }
    AsyncImage(model = request, contentDescription = null, contentScale = contentScale, modifier = modifier)
}

/** Start value for enter animations: already "arrived" when rendered in a preview. */
@Composable
fun enterStart(): Float = if (LocalInspectionMode.current) 1f else 0f
