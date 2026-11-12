package com.revive.recovery.data

import androidx.compose.ui.graphics.Color
import java.io.File

/** Where a recoverable image was found. */
enum class Source(val title: String, val subtitle: String, val color: Color, val short: String) {
    TRASH("Recently Deleted", "Items in the system trash", Color(0xFFFF3B30), "Deleted"),
    THUMBNAIL("Thumbnail Cache", "Previews left behind after deletion", Color(0xFFFF9500), "Thumbs"),
    HIDDEN("Hidden & Disguised", "Hidden folders and renamed files", Color(0xFF5856D6), "Hidden"),
    APP("App Media", "WhatsApp, Telegram, Instagram & more", Color(0xFF34C759), "Apps"),
    CARVED("Fragments", "Images rebuilt from cache data", Color(0xFFAF52DE), "Fragments"),
    ROOT("Deep Scan", "App caches read with root access", Color(0xFF00C7BE), "Root"),
    GALLERY("Gallery", "Photos that still exist", Color(0xFF007AFF), "Gallery"),
}

data class FoundImage(
    val id: String,
    /** A readable file holding the image bytes (original file, or an extracted copy in app cache). */
    val file: File,
    /** Where it originally came from (shown to the user). */
    val originPath: String,
    val source: Source,
    val sizeBytes: Long,
    val modified: Long,
    val mime: String,
    val width: Int = 0,
    val height: Int = 0,
)

data class ScanOptions(
    val trash: Boolean = true,
    val thumbnails: Boolean = true,
    val hidden: Boolean = true,
    val apps: Boolean = true,
    val carve: Boolean = true,
    val root: Boolean = false,
    val gallery: Boolean = false,
    val minSizeKb: Int = 4,
)

sealed interface ScanEvent {
    data class Progress(val fraction: Float, val phase: String, val path: String, val scanned: Int) : ScanEvent
    data class Found(val image: FoundImage) : ScanEvent
    data class Done(val scanned: Int, val millis: Long) : ScanEvent
}
