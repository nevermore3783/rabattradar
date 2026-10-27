package at.rabattradar.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, val url: String) : IOException("HTTP $code für $url")

/** Minimal HTTP abstraction so sources can be tested with canned responses. */
fun interface Http {
    suspend fun get(url: String, headers: Map<String, String>): String
}

suspend fun Http.get(url: String): String = get(url, emptyMap())

class OkHttp(
    private val client: OkHttpClient = defaultClient(),
    private val retries: Int = 2,
) : Http {

    override suspend fun get(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        var attempt = 0
        var result: String? = null
        while (result == null) {
            try {
                result = execute(url, headers)
            } catch (e: HttpException) {
                // 4xx (except throttling) will not get better by retrying.
                if (e.code in 400..499 && e.code != 408 && e.code != 429 || attempt >= retries) throw e
            } catch (e: IOException) {
                if (attempt >= retries) throw e
            }
            if (result == null) {
                attempt++
                delay(1500L * attempt)
            }
        }
        result
    }

    private fun execute(url: String, headers: Map<String, String>): String {
        val builder = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
            .header("Accept-Language", "de-AT,de;q=0.9,en;q=0.5")
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, url)
            return response.body?.string() ?: ""
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
