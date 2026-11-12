package com.revive.recovery.data

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Uses `su` (if the device is rooted and the user grants it) to copy image-looking
 * files out of app-private storage (/data/data/<pkg>/cache, files, app_*) into our cache,
 * where they can be previewed and restored. Nothing on /data is modified.
 */
class RootScanner(private val stageDir: File) {

    companion object {
        fun isRooted(): Boolean = try {
            val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val ok = p.waitFor(6, TimeUnit.SECONDS) && p.inputStream.bufferedReader().readText().contains("uid=0")
            p.destroy()
            ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun run(minBytes: Int, onImage: suspend (File, String, String) -> Unit) {
        stageDir.deleteRecursively()
        stageDir.mkdirs()
        val stage = stageDir.absolutePath
        val minK = (minBytes / 1024).coerceAtLeast(1)
        // For each candidate file read its first 4 bytes; copy if they match a JPEG/PNG/GIF/WEBP signature.
        val script = """
            n=0
            for d in /data/data/*/cache /data/data/*/files /data/data/*/app_* /data/user_de/0/*/cache; do
              [ -d "${'$'}d" ] || continue
              find "${'$'}d" -type f -size +${minK}k -size -30M 2>/dev/null | while read -r f; do
                h=${'$'}(head -c 4 "${'$'}f" 2>/dev/null | od -An -tx1 | tr -d ' \n')
                case "${'$'}h" in
                  ffd8ff*|89504e47|47494638|52494646)
                    n=${'$'}((n+1)); t="$stage/r_${'$'}{RANDOM}_${'$'}n"
                    cp "${'$'}f" "${'$'}t" 2>/dev/null && echo "${'$'}t|${'$'}f" ;;
                esac
              done
            done
            chmod -R 777 "$stage" 2>/dev/null
        """.trimIndent()
        val proc = try {
            ProcessBuilder("su").redirectErrorStream(false).start()
        } catch (_: Exception) {
            return
        }
        proc.outputStream.bufferedWriter().use { it.write(script); it.write("\nexit\n") }
        val hits = proc.inputStream.bufferedReader().readLines()
        proc.waitFor(10, TimeUnit.MINUTES)
        for (line in hits) {
            val parts = line.split('|', limit = 2)
            if (parts.size != 2) continue
            val f = File(parts[0])
            if (!f.canRead()) continue
            val mime = ImageMagic.detect(f) ?: continue
            onImage(f, parts[1], mime)
        }
    }
}
