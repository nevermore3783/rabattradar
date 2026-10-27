package at.rabattradar.core

import at.rabattradar.core.net.Http
import at.rabattradar.core.net.HttpException

object Fixtures {
    fun read(name: String): String =
        requireNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    /** Serves canned responses by URL substring; the first page of a paginated API is enough for the fixtures. */
    fun http(vararg routes: Pair<String, String>) = Http { url, _ ->
        routes.firstOrNull { url.contains(it.first) }?.second ?: throw HttpException(404, url)
    }

    const val MARKTGURU_HOME = """<script type="application/json">{"config":{"apiKey":"test-api","clientKey":"test-client"}}</script>"""
}
