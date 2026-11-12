package com.revive.recovery.data

import java.io.File
import java.io.RandomAccessFile

/** Detects image formats by their leading bytes, independent of the file extension. */
object ImageMagic {
    fun detect(head: ByteArray, len: Int = head.size): String? {
        if (len < 12) return null
        fun b(i: Int) = head[i].toInt() and 0xFF
        return when {
            b(0) == 0xFF && b(1) == 0xD8 && b(2) == 0xFF -> "image/jpeg"
            b(0) == 0x89 && b(1) == 0x50 && b(2) == 0x4E && b(3) == 0x47 -> "image/png"
            b(0) == 0x47 && b(1) == 0x49 && b(2) == 0x46 && b(3) == 0x38 -> "image/gif"
            b(0) == 0x52 && b(1) == 0x49 && b(2) == 0x46 && b(3) == 0x46 &&
                b(8) == 0x57 && b(9) == 0x45 && b(10) == 0x42 && b(11) == 0x50 -> "image/webp"
            b(4) == 0x66 && b(5) == 0x74 && b(6) == 0x79 && b(7) == 0x70 -> {
                val brand = String(head, 8, 4, Charsets.US_ASCII)
                when (brand) {
                    "heic", "heix", "hevc", "heim", "heis", "mif1", "msf1" -> "image/heic"
                    "avif", "avis" -> "image/avif"
                    else -> null
                }
            }
            b(0) == 0x42 && b(1) == 0x4D -> "image/bmp"
            else -> null
        }
    }

    fun detect(file: File): String? = try {
        RandomAccessFile(file, "r").use { raf ->
            val buf = ByteArray(16)
            val n = raf.read(buf)
            if (n < 12) null else detect(buf, n)
        }
    } catch (_: Exception) {
        null
    }

    fun extensionFor(mime: String) = when (mime) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/avif" -> "avif"
        "image/bmp" -> "bmp"
        else -> "jpg"
    }

    val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp", "dng")
}
