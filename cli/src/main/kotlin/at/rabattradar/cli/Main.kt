package at.rabattradar.cli

import at.rabattradar.core.Aggregator
import at.rabattradar.core.Snapshot
import at.rabattradar.core.model.infoBar
import at.rabattradar.core.search.OfferIndex
import at.rabattradar.core.search.OfferQuery
import at.rabattradar.core.search.ValidityFilter
import at.rabattradar.core.source.FetchContext
import at.rabattradar.core.text.euro
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Live scrape from the command line - useful to check the scrapers without a phone:
 *
 *   ./gradlew :cli:run --args="--zip 1010 --out offers.json --search milka"
 */
fun main(args: Array<String>): Unit = runBlocking {
    fun opt(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val ctx = FetchContext(zipCode = opt("--zip") ?: "1010")
    val snapshot = Aggregator.default().fetch(ctx)

    println("Quellen:")
    snapshot.statuses.forEach { s ->
        println("  %-22s %s %5d Angebote  %5d ms %s".format(s.source.displayName, if (s.ok) "OK " else "ERR", s.count, s.millis, s.error ?: ""))
    }
    println("\nNach dem Zusammenführen: ${snapshot.offers.size} Angebote")
    snapshot.offers.groupBy { it.chain }.forEach { (chain, list) ->
        val active = list.count { it.isActiveOn(ctx.today) }
        val withConditions = list.count { it.conditions.isNotEmpty() }
        val noImage = list.count { it.imageUrl == null }
        println("  %-6s %4d gesamt, %4d jetzt gültig, %4d mit Bedingungen, %d ohne Bild".format(chain.displayName, list.size, active, withConditions, noImage))
    }

    opt("--out")?.let { path ->
        File(path).writeText(Json { prettyPrint = true }.encodeToString(Snapshot.serializer(), snapshot))
        println("\nGespeichert: $path")
    }

    opt("--search")?.let { term ->
        val hits = OfferIndex(snapshot.offers).search(OfferQuery(text = term, validity = ValidityFilter.ALL), ctx.today)
        println("\nSuche \"$term\": ${hits.size} Treffer")
        hits.take(25).forEach { o ->
            val bar = o.infoBar()
            println(
                "  [%-5s] %-45s %7s €  %-14s %-18s %-20s %s–%s  %s".format(
                    o.chain.displayName, o.title.take(45), o.price.euro(),
                    bar.headline, bar.conditionLine ?: "", bar.reference ?: "", o.validFrom ?: "?", o.validTo ?: "?",
                    o.conditions.joinToString(", ") { it.label },
                ),
            )
        }
    }
}
