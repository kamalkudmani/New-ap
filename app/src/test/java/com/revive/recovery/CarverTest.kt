package com.revive.recovery

import com.revive.recovery.data.Carver
import com.revive.recovery.data.ImageMagic
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.random.Random

/**
 * Plain-JVM tests of the recovery engine. Test images are assembled byte-by-byte
 * (JPEG markers / PNG chunks), and validated with a header parser instead of android.graphics.
 */
class CarverTest {
    @get:Rule val tmp = TemporaryFolder()
    private val rnd = Random(42)

    /** Entropy-coded-looking payload with no 0xFF bytes (as in real JPEG scans after byte stuffing). */
    private fun scanData(n: Int) = ByteArray(n) { (rnd.nextInt(0xFE)).toByte() }

    private fun jpeg(w: Int, h: Int, payload: Int, exifThumb: ByteArray? = null): ByteArray {
        val o = ByteArrayOutputStream(); val d = DataOutputStream(o)
        d.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        if (exifThumb != null) { // APP1 carrying an embedded thumbnail JPEG (nested SOI/EOI)
            d.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte())); d.writeShort(2 + 6 + exifThumb.size)
            d.write("Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)); d.write(exifThumb)
        }
        d.write(byteArrayOf(0xFF.toByte(), 0xC0.toByte())); d.writeShort(11); d.writeByte(8); d.writeShort(h); d.writeShort(w)
        d.writeByte(1); d.writeByte(1); d.writeByte(0x11); d.writeByte(0)
        d.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte())); d.writeShort(8); d.writeByte(1); d.writeByte(1); d.writeByte(0); d.writeByte(0); d.writeByte(63); d.writeByte(0)
        d.write(scanData(payload))
        d.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return o.toByteArray()
    }

    private fun png(w: Int, h: Int): ByteArray {
        val o = ByteArrayOutputStream(); val d = DataOutputStream(o)
        d.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(type: String, data: ByteArray) {
            d.writeInt(data.size); val t = type.toByteArray(); d.write(t); d.write(data)
            d.writeInt(CRC32().apply { update(t); update(data) }.value.toInt())
        }
        chunk("IHDR", ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(w); writeInt(h); write(byteArrayOf(8, 2, 0, 0, 0)) } }.toByteArray())
        val raw = rnd.nextBytes((w * 3 + 1) * h) // incompressible, so the PNG is a realistic size
        val def = Deflater().apply { setInput(raw); finish() }
        val buf = ByteArray(raw.size + 64); val n = def.deflate(buf)
        chunk("IDAT", buf.copyOf(n)); chunk("IEND", ByteArray(0))
        return o.toByteArray()
    }

    /** Reads dimensions from SOF0 (JPEG) or IHDR (PNG). */
    private val headerBounds: (ByteArray) -> Pair<Int, Int>? = { b ->
        fun u16(i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
        fun u32(i: Int) = (u16(i) shl 16) or u16(i + 2)
        when (ImageMagic.detect(b)) {
            "image/png" -> u32(16) to u32(20)
            "image/jpeg" -> {
                var i = 2; var r: Pair<Int, Int>? = null
                while (i + 4 < b.size && r == null) {
                    if (b[i] != 0xFF.toByte()) break
                    val m = b[i + 1].toInt() and 0xFF
                    if (m == 0xC0) r = u16(i + 7) to u16(i + 5) else i += 2 + u16(i + 2)
                }
                r
            }
            else -> null
        }
    }

    private fun carve(file: File, minBytes: Int = 512): List<Triple<File, String, Pair<Int, Int>>> {
        val found = mutableListOf<Triple<File, String, Pair<Int, Int>>>()
        Carver(tmp.newFolder(), minBytes, headerBounds).carve(file) { f, m, w, h -> found += Triple(f, m, w to h) }
        return found
    }

    @Test
    fun extractsJpegAndPngEmbeddedInJunk() {
        val j = jpeg(1200, 900, 40_000)
        val p = png(64, 48)
        val blob = rnd.nextBytes(30_000) + j +
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00) + // false-positive header
            scanData(20_000) + p + rnd.nextBytes(5_000)
        val found = carve(tmp.newFile("thumbdata3--1967290299").apply { writeBytes(blob) })

        assertEquals(2, found.size)
        assertEquals("image/jpeg", found[0].second)
        assertEquals(1200 to 900, found[0].third)
        assertArrayEquals(j, found[0].first.readBytes())
        assertEquals("image/png", found[1].second)
        assertEquals(64 to 48, found[1].third)
        assertArrayEquals(p, found[1].first.readBytes())
    }

    @Test
    fun keepsExifThumbnailInsideTheFullImage() {
        // A JPEG whose EXIF block contains its own thumbnail JPEG: must come out as ONE full image.
        val thumb = jpeg(160, 120, 3_000)
        val full = jpeg(4000, 3000, 60_000, exifThumb = thumb)
        val found = carve(tmp.newFile("cache.db").apply { writeBytes(scanData(1_000) + full + scanData(1_000)) })
        assertEquals(1, found.size)
        assertEquals(4000 to 3000, found[0].third)
        assertArrayEquals(full, found[0].first.readBytes())
    }

    @Test
    fun carvesImageSpanningTheReadWindow() {
        val j = jpeg(640, 480, 9_000)
        val blob = ByteArray(4 * 1024 * 1024 - 1_000) + j + ByteArray(2_000)
        val found = carve(tmp.newFile("imgcache.0").apply { writeBytes(blob) })
        assertEquals(1, found.size)
        assertArrayEquals(j, found[0].first.readBytes())
    }

    @Test
    fun ignoresTinyImagesAndTruncatedStreams() {
        val tiny = jpeg(16, 16, 900)                   // icon-sized → rejected
        val truncated = jpeg(800, 600, 5_000).copyOf(3_000) // no EOI → rejected
        val found = carve(tmp.newFile("blob.bin").apply { writeBytes(tiny + scanData(2_000) + truncated + scanData(500)) })
        assertEquals(0, found.size)
    }

    @Test
    fun detectsImagesByMagicBytesNotExtension() {
        assertEquals("image/jpeg", ImageMagic.detect(tmp.newFile("notes.txt").apply { writeBytes(jpeg(50, 50, 100)) }))
        assertEquals("image/png", ImageMagic.detect(tmp.newFile("x.bin").apply { writeBytes(png(10, 10)) }))
        assertEquals("image/gif", ImageMagic.detect(tmp.newFile("y").apply { writeBytes("GIF89a\u0001\u0000\u0001\u0000\u0000\u0000".toByteArray()) }))
        val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)
        assertEquals("image/webp", ImageMagic.detect(tmp.newFile("z.dat").apply { writeBytes(webp) }))
        val heic = byteArrayOf(0, 0, 0, 0x18) + "ftypheic\u0000\u0000\u0000\u0000".toByteArray()
        assertEquals("image/heic", ImageMagic.detect(tmp.newFile("h").apply { writeBytes(heic) }))
        assertNull(ImageMagic.detect(tmp.newFile("real.txt").apply { writeText("hello world, definitely not an image") }))
    }
}
