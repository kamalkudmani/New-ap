package com.revive.recovery.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Copies recovered images into Pictures/Revive so they show up in every gallery app. */
class Restorer(private val context: Context) {

    companion object { const val ALBUM = "Revive" }

    suspend fun restore(images: List<FoundImage>, onProgress: (Int) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        var ok = 0
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        images.forEachIndexed { i, img ->
            val ext = ImageMagic.extensionFor(img.mime)
            val name = "Revive_${img.source.name.lowercase()}_${stamp}_${i + 1}.$ext"
            if (runCatching { save(img, name) }.getOrDefault(false)) ok++
            onProgress(i + 1)
        }
        ok
    }

    private fun save(img: FoundImage, name: String): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, img.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
                if (img.modified > 0) put(MediaStore.Images.Media.DATE_TAKEN, img.modified)
            }
            val resolver = context.contentResolver
            val uri: Uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out -> img.file.inputStream().use { it.copyTo(out) } } ?: return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return true
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
            dir.mkdirs()
            val out = File(dir, name)
            img.file.copyTo(out, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf(img.mime), null)
            return true
        }
    }

    /** Lists files already restored into Pictures/Revive. */
    fun listRestored(): List<File> {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        return (dir.listFiles() ?: emptyArray()).filter { it.isFile && !it.name.startsWith(".") }
            .sortedByDescending { it.lastModified() }
    }
}
