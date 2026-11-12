package com.revive.recovery.data

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Multi-phase recovery scanner.
 *
 *  1. System trash (MediaStore "trashed" items + `.trashed-*` files)          Android 11+
 *  2. Full storage walk, including hidden/dot folders and `.nomedia` folders,
 *     classifying every image by magic bytes (so renamed/extensionless images are found)
 *  3. Carving: thumbnail databases and cache blobs are parsed for embedded JPEG/PNG
 *  4. Root (optional): app-private caches under /data are copied out via `su` and checked
 */
class ScanEngine(private val context: Context) {

    private val carvedDir = File(context.cacheDir, "carved")
    private val rootDir = File(context.cacheDir, "rootscan")

    private val appFolders = listOf(
        "whatsapp", "com.whatsapp", "telegram", "org.telegram", "instagram", "snapchat",
        "messenger", "com.facebook", "viber", "signal", "wechat", "tencent", "line", "discord",
        "twitter", "tiktok", "pinterest", "reddit",
    )

    private val carveNameHints = listOf("thumbdata", "imgcache", ".thumbcache", "cache", ".db", "journal", ".dat", ".bin", ".tmp")

    fun scan(options: ScanOptions): Flow<ScanEvent> = channelFlow {
        val started = System.currentTimeMillis()
        val seen = HashSet<String>()
        var scanned = 0
        val minBytes = options.minSizeKb * 1024

        suspend fun emitFound(img: FoundImage) {
            if (seen.add(img.id)) send(ScanEvent.Found(img))
        }

        val phases = buildList {
            if (options.trash) add("trash")
            add("storage")
            if (options.root) add("root")
        }
        fun frac(phase: String, inner: Float) =
            (phases.indexOf(phase) + inner.coerceIn(0f, 1f)) / phases.size

        // ---------- Phase 1: system trash ----------
        if (options.trash && Build.VERSION.SDK_INT >= 30) {
            send(ScanEvent.Progress(frac("trash", 0f), "Checking Recently Deleted", "MediaStore", scanned))
            queryTrash().forEach { img ->
                ensureActive()
                scanned++
                if (img.sizeBytes >= minBytes) emitFound(img)
            }
        }

        // ---------- Phase 2: full storage walk ----------
        val root = Environment.getExternalStorageDirectory()
        val topLevel = (root.listFiles() ?: emptyArray()).sortedBy { it.name.lowercase() }
        val carver = Carver(carvedDir, minBytes)
        topLevel.forEachIndexed { idx, top ->
            ensureActive()
            send(ScanEvent.Progress(frac("storage", idx / topLevel.size.toFloat()), "Scanning storage", top.absolutePath, scanned))
            walk(top, hiddenContext = top.name.startsWith(".")) { file, hidden, inThumbDir ->
                ensureActive()
                scanned++
                if (scanned % 150 == 0) {
                    send(ScanEvent.Progress(frac("storage", idx / topLevel.size.toFloat()), "Scanning storage", file.parent ?: "", scanned))
                }
                handleFile(file, hidden, inThumbDir, options, minBytes, carver) { emitFound(it) }
            }
        }

        // ---------- Phase 3: root deep scan ----------
        if (options.root) {
            send(ScanEvent.Progress(frac("root", 0f), "Deep scan (root)", "/data/data", scanned))
            RootScanner(rootDir).run(minBytes) { file, origin, mime ->
                scanned++
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                if (opts.outWidth >= 32 && opts.outHeight >= 32) {
                    emitFound(
                        FoundImage(
                            id = "root:$origin", file = file, originPath = origin, source = Source.ROOT,
                            sizeBytes = file.length(), modified = file.lastModified(), mime = mime,
                            width = opts.outWidth, height = opts.outHeight,
                        )
                    )
                }
            }
        }

        send(ScanEvent.Progress(1f, "Finishing", "", scanned))
        send(ScanEvent.Done(scanned, System.currentTimeMillis() - started))
    }.flowOn(Dispatchers.IO)

    private suspend fun ProducerScope<ScanEvent>.handleFile(
        file: File, hidden: Boolean, inThumbDir: Boolean, options: ScanOptions, minBytes: Int,
        carver: Carver, emit: suspend (FoundImage) -> Unit,
    ) {
        val len = file.length()
        if (len < minBytes) return
        val name = file.name.lowercase()
        val path = file.absolutePath
        val lowerPath = path.lowercase()
        val ext = name.substringAfterLast('.', "")
        val hasImageExt = ext in ImageMagic.imageExtensions

        // Trashed files live on disk as ".trashed-<expiry>-<name>"
        val isTrashed = name.startsWith(".trashed-")
        val mime = ImageMagic.detect(file)

        if (mime != null) {
            val source = when {
                isTrashed -> if (options.trash) Source.TRASH else null
                inThumbDir -> if (options.thumbnails) Source.THUMBNAIL else null
                appFolders.any { lowerPath.contains("/$it") } && (hidden || name.startsWith(".")) ->
                    if (options.hidden) Source.HIDDEN else if (options.apps) Source.APP else null
                appFolders.any { lowerPath.contains("/$it") } -> if (options.apps) Source.APP else null
                hidden || !hasImageExt -> if (options.hidden) Source.HIDDEN else null
                else -> if (options.gallery) Source.GALLERY else null
            } ?: return
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            if (mime != "image/heic" && mime != "image/avif" && (opts.outWidth <= 0 || opts.outHeight <= 0)) return
            if (opts.outWidth in 1..31 && opts.outHeight in 1..31) return // icons, sprites
            emit(
                FoundImage(
                    id = path, file = file, originPath = path, source = source, sizeBytes = len,
                    modified = file.lastModified(), mime = mime, width = opts.outWidth, height = opts.outHeight,
                )
            )
            return
        }

        // Not an image itself – maybe a container with images inside (thumbnail DBs, caches).
        if (options.carve && len >= 16 * 1024 && !hasImageExt && shouldCarve(name, lowerPath, inThumbDir, hidden)) {
            val fromThumbs = inThumbDir || name.startsWith("thumbdata") || name.contains("thumb")
            val pending = ArrayList<FoundImage>()
            carver.carve(file) { out, m, w, h ->
                pending += FoundImage(
                    id = out.name, file = out, originPath = "$path (embedded)",
                    source = if (fromThumbs && options.thumbnails) Source.THUMBNAIL else Source.CARVED,
                    sizeBytes = out.length(), modified = file.lastModified(), mime = m, width = w, height = h,
                )
            }
            pending.forEach { emit(it) }
        }
    }

    private fun shouldCarve(name: String, lowerPath: String, inThumbDir: Boolean, hidden: Boolean): Boolean {
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".apk") || name.endsWith(".zip") ||
            name.endsWith(".mp3") || name.endsWith(".pdf") || name.endsWith(".obb")
        ) return false
        if (inThumbDir) return true
        if (carveNameHints.any { name.contains(it) }) return true
        return hidden && (lowerPath.contains("cache") || lowerPath.contains("thumb"))
    }

    /** Depth-first walk that tracks whether we're inside a hidden / .nomedia / thumbnail folder. */
    private suspend fun walk(
        dir: File, hiddenContext: Boolean, depth: Int = 0,
        thumbContext: Boolean = false,
        onFile: suspend (File, Boolean, Boolean) -> Unit,
    ) {
        if (depth > 24) return
        if (dir.isFile) { onFile(dir, hiddenContext, thumbContext); return }
        val children = dir.listFiles() ?: return
        val isHidden = hiddenContext || dir.name.startsWith(".") || children.any { it.name == ".nomedia" }
        val isThumb = thumbContext || dir.name.lowercase().let { it.contains("thumbnail") || it == ".thumbs" || it.contains("thumbcache") }
        for (child in children) {
            if (child.isDirectory) {
                val n = child.name
                if (depth == 0 && dir.name == "Android" && (n == "obb")) continue
                walk(child, isHidden, depth + 1, isThumb, onFile)
            } else {
                onFile(child, isHidden || child.name.startsWith("."), isThumb)
            }
        }
    }

    private fun queryTrash(): List<FoundImage> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        val out = ArrayList<FoundImage>()
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT, MediaStore.MediaColumns.IS_TRASHED,
        )
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'image/%'")
        }
        try {
            context.contentResolver.query(uri, projection, args, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iData = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                while (c.moveToNext()) {
                    val path = c.getString(iData) ?: continue
                    val f = File(path)
                    if (!f.canRead()) continue
                    out += FoundImage(
                        id = path, file = f, originPath = path, source = Source.TRASH,
                        sizeBytes = c.getLong(iSize), modified = TimeUnit.SECONDS.toMillis(c.getLong(iDate)),
                        mime = c.getString(iMime) ?: "image/jpeg", width = c.getInt(iW), height = c.getInt(iH),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}
