package at.rabattradar.cli

import at.rabattradar.core.net.Http
import at.rabattradar.core.net.HttpException
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Development helpers for the CLI:
 *  - [Recording] logs every request (start, duration, size) to see where a refresh spends its time,
 *  - [Cached] stores responses in a directory and replays them, so code changes can be compared on identical input.
 */
object DevHttp {

    class Entry(val url: String, val startMs: Long, val durationMs: Long, val bytes: Int, val status: Int)

    class Recording(private val inner: Http) : Http {
        private val t0 = System.currentTimeMillis()
        val entries = ConcurrentLinkedQueue<Entry>()

        override suspend fun get(url: String, headers: Map<String, String>): String {
            val start = System.currentTimeMillis()
            try {
                return inner.get(url, headers).also { entries += Entry(url, start - t0, System.currentTimeMillis() - start, it.length, 200) }
            } catch (e: HttpException) {
                entries += Entry(url, start - t0, System.currentTimeMillis() - start, 0, e.code)
                throw e
            }
        }

        fun print() {
            println("\nRequests (start +duration, chars):")
            entries.sortedBy { it.startMs }.forEach { e ->
                println("  %6d +%5d ms  %4d %9d  %s".format(e.startMs, e.durationMs, e.status, e.bytes, e.url.take(140)))
            }
            println("  %d requests, %.1f MB".format(entries.size, entries.sumOf { it.bytes } / 1e6))
        }
    }

    class Cached(private val inner: Http, private val dir: File) : Http {
        init { dir.mkdirs() }

        private fun key(url: String) = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

        override suspend fun get(url: String, headers: Map<String, String>): String {
            val body = File(dir, key(url))
            val status = File(dir, key(url) + ".status")
            if (status.exists()) {
                val code = status.readText().toInt()
                if (code != 200) throw HttpException(code, url)
                return body.readText()
            }
            return try {
                inner.get(url, headers).also { body.writeText(it); status.writeText("200") }
            } catch (e: HttpException) {
                status.writeText("${e.code}")
                throw e
            }
        }
    }
}
