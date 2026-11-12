package com.revive.recovery.data

import android.graphics.BitmapFactory
import java.io.File
import java.io.RandomAccessFile

/**
 * File carver: scans an arbitrary blob (thumbnail databases, cache files, app data)
 * for embedded JPEG and PNG streams and extracts each one as a standalone image.
 *
 * JPEG: SOI (FF D8 FF) … EOI (FF D9), tracking nested SOI markers from EXIF thumbnails.
 * PNG:  89 50 4E 47 0D 0A 1A 0A … IEND chunk + CRC.
 */
class Carver(
    private val outDir: File,
    private val minBytes: Int,
    /** Returns (width, height) if the bytes decode as an image; validates carved candidates. */
    private val bounds: (ByteArray) -> Pair<Int, Int>? = ::androidBounds,
) {
    companion object {
        fun androidBounds(bytes: ByteArray): Pair<Int, Int>? {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
        }
    }


    private val maxImageBytes = 24 * 1024 * 1024

    fun carve(file: File, onImage: (File, String, Int, Int) -> Unit): Int {
        val length = file.length()
        if (length < minBytes || length > 512L * 1024 * 1024) return 0
        var count = 0
        RandomAccessFile(file, "r").use { raf ->
            val window = 4 * 1024 * 1024
            val buf = ByteArray(window)
            var base = 0L
            while (base < length) {
                raf.seek(base)
                val n = raf.read(buf, 0, window)
                if (n <= 0) break
                var i = 0
                var advanceTo = -1L
                while (i < n - 8) {
                    val b0 = buf[i].toInt() and 0xFF
                    if (b0 == 0xFF && (buf[i + 1].toInt() and 0xFF) == 0xD8 && (buf[i + 2].toInt() and 0xFF) == 0xFF) {
                        val end = findJpegEnd(raf, base + i, length)
                        if (end > 0 && extract(raf, base + i, end, "image/jpeg", file, count, onImage)) {
                            count++
                            advanceTo = end
                            break
                        }
                    } else if (b0 == 0x89 && buf[i + 1] == 0x50.toByte() && buf[i + 2] == 0x4E.toByte() &&
                        buf[i + 3] == 0x47.toByte() && buf[i + 4] == 0x0D.toByte() && buf[i + 5] == 0x0A.toByte()
                    ) {
                        val end = findPngEnd(raf, base + i, length)
                        if (end > 0 && extract(raf, base + i, end, "image/png", file, count, onImage)) {
                            count++
                            advanceTo = end
                            break
                        }
                    }
                    i++
                }
                base = when {
                    advanceTo > 0 -> advanceTo
                    n < window -> length
                    else -> base + n - 8
                }
            }
        }
        return count
    }

    /** Cached random-access byte reader so marker parsing doesn't hit the disk per byte. */
    private class Bytes(private val raf: RandomAccessFile, private val length: Long) {
        private val buf = ByteArray(64 * 1024)
        private var bufStart = -1L
        private var bufLen = 0
        fun at(pos: Long): Int {
            if (pos < 0 || pos >= length) return -1
            if (bufStart < 0 || pos < bufStart || pos >= bufStart + bufLen) {
                raf.seek(pos); bufLen = maxOf(0, raf.read(buf)); bufStart = pos
                if (bufLen <= 0) return -1
            }
            return buf[(pos - bufStart).toInt()].toInt() and 0xFF
        }
        fun u16(pos: Long): Int { val a = at(pos); val b = at(pos + 1); return if (a < 0 || b < 0) -1 else (a shl 8) or b }
    }

    private fun isSegmentMarker(m: Int) =
        m in 0xC0..0xCF || m in 0xDB..0xDF || m in 0xE0..0xEF || m == 0xFE

    /**
     * Parses the JPEG segment structure starting at SOI and returns the offset just past EOI, or -1.
     * Segment lengths are honoured, so EXIF thumbnails (inside APP1) are skipped rather than
     * mistaken for the end of the image, and random bytes that merely look like SOI are rejected.
     */
    private fun findJpegEnd(raf: RandomAccessFile, start: Long, length: Long): Long {
        val r = Bytes(raf, length)
        val limit = minOf(length, start + maxImageBytes)
        var pos = start + 2
        var sawFrame = false
        var sawScan = false
        while (pos + 4 <= limit) {
            if (r.at(pos) != 0xFF) return -1
            var m = r.at(pos + 1)
            while (m == 0xFF) { pos++; m = r.at(pos + 1) }         // fill bytes
            when {
                m == 0xD9 -> return if (sawFrame && sawScan) pos + 2 else -1
                m == 0xDA -> {
                    val len = r.u16(pos + 2)
                    if (!sawFrame || len < 6) return -1
                    sawScan = true
                    pos += 2 + len
                    // Entropy-coded data: only FF00 (stuffing), FFD0–D7 (restart) and FFFF (fill) may appear.
                    while (pos + 1 < limit) {
                        val b = r.at(pos)
                        if (b < 0) return -1
                        if (b != 0xFF) { pos++; continue }
                        val n = r.at(pos + 1)
                        if (n == 0x00 || n in 0xD0..0xD7) { pos += 2; continue }
                        if (n == 0xFF) { pos++; continue }
                        break // a real marker: EOI, or DHT/SOS of the next progressive scan
                    }
                }
                isSegmentMarker(m) -> {
                    val len = r.u16(pos + 2)
                    if (len < 2) return -1
                    if (m in 0xC0..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) {
                        if (len < 8 || r.u16(pos + 5) <= 0 || r.u16(pos + 7) <= 0) return -1
                        sawFrame = true
                    }
                    pos += 2 + len
                }
                else -> return -1
            }
        }
        return -1
    }

    private fun findPngEnd(raf: RandomAccessFile, start: Long, length: Long): Long {
        var pos = start + 8
        val hdr = ByteArray(8)
        val limit = minOf(length, start + maxImageBytes)
        while (pos + 12 <= limit) {
            raf.seek(pos)
            if (raf.read(hdr) != 8) return -1
            val len = ((hdr[0].toLong() and 0xFF) shl 24) or ((hdr[1].toLong() and 0xFF) shl 16) or
                ((hdr[2].toLong() and 0xFF) shl 8) or (hdr[3].toLong() and 0xFF)
            if (len < 0 || len > maxImageBytes) return -1
            val type = String(hdr, 4, 4, Charsets.US_ASCII)
            if (!type.all { it.isLetter() }) return -1
            pos += 12 + len
            if (type == "IEND") return if (pos <= length) pos else -1
        }
        return -1
    }

    private fun extract(
        raf: RandomAccessFile, start: Long, end: Long, mime: String,
        container: File, index: Int, onImage: (File, String, Int, Int) -> Unit,
    ): Boolean {
        val size = end - start
        if (size < minBytes || size > maxImageBytes) return false
        val bytes = ByteArray(size.toInt())
        raf.seek(start)
        raf.readFully(bytes)
        val (w, h) = bounds(bytes) ?: return false
        if (w < 32 || h < 32) return false
        outDir.mkdirs()
        val name = "${container.absolutePath.hashCode().toUInt().toString(16)}_${start}_$index.${ImageMagic.extensionFor(mime)}"
        val out = File(outDir, name)
        if (!out.exists()) out.writeBytes(bytes)
        onImage(out, mime, w, h)
        return true
    }
}
