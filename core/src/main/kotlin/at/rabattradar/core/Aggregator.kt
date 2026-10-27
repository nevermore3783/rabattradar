package at.rabattradar.core

import at.rabattradar.core.merge.OfferMerger
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.net.Http
import at.rabattradar.core.net.OkHttp
import at.rabattradar.core.source.BillaFlyerText
import at.rabattradar.core.source.BillaShopSource
import at.rabattradar.core.source.FlyerPage
import at.rabattradar.core.source.FetchContext
import at.rabattradar.core.source.LidlSource
import at.rabattradar.core.source.MarktguruSource
import at.rabattradar.core.source.OfferSource
import at.rabattradar.core.source.PennySource
import at.rabattradar.core.source.SparShopSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

@Serializable
data class SourceStatus(
    val source: SourceId,
    val ok: Boolean,
    val count: Int,
    val error: String? = null,
    val millis: Long = 0,
)

@Serializable
data class Snapshot(
    val fetchedAt: Long,
    val zipCode: String,
    val offers: List<Offer>,
    val statuses: List<SourceStatus>,
)

/** Runs all sources in parallel, tolerates partial failures and merges the result. */
class Aggregator(
    private val sources: List<OfferSource>,
    private val billaFlyer: BillaFlyerText? = null,
) {

    suspend fun fetch(
        ctx: FetchContext,
        chains: Set<Chain> = Chain.entries.toSet(),
        enabledSources: Set<SourceId> = SourceId.entries.toSet(),
    ): Snapshot = coroutineScope {
        val jobs = sources
            .filter { it.id in enabledSources && it.chains.any { c -> c in chains } }
            .map { source ->
                async {
                    val start = System.currentTimeMillis()
                    try {
                        val offers = if (source is MarktguruSource) source.fetch(ctx, chains) else source.fetch(ctx)
                        offers to SourceStatus(source.id, true, offers.size, null, System.currentTimeMillis() - start)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList<Offer>() to SourceStatus(source.id, false, 0, e.message ?: e.javaClass.simpleName, System.currentTimeMillis() - start)
                    }
                }
            }
        val flyerJob = billaFlyer?.takeIf { Chain.BILLA in chains && it.id in enabledSources }?.let { flyer ->
            async {
                val start = System.currentTimeMillis()
                try {
                    flyer.pages(ctx.today) to null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList<FlyerPage>() to SourceStatus(flyer.id, false, 0, e.message ?: e.javaClass.simpleName, System.currentTimeMillis() - start)
                } to start
            }
        }
        val results = jobs.awaitAll()
        val all = results.flatMap { it.first }.filter { it.chain in chains && !it.isExpired(ctx.today) }
        var offers = OfferMerger.merge(all)
        val statuses = results.map { it.second }.toMutableList()

        flyerJob?.await()?.let { (result, start) ->
            val (pages, failure) = result
            if (failure != null) {
                statuses += failure
            } else {
                val before = offers
                offers = BillaFlyerText.enrichAll(offers, pages)
                val enriched = offers.indices.count { offers[it] !== before[it] }
                statuses += SourceStatus(SourceId.BILLA_FLYER, pages.isNotEmpty(), enriched,
                    if (pages.isEmpty()) "Kein Flugblatt-Text gefunden" else null, System.currentTimeMillis() - start)
            }
        }
        Snapshot(
            fetchedAt = System.currentTimeMillis(),
            zipCode = ctx.zipCode,
            offers = offers,
            statuses = statuses,
        )
    }

    companion object {
        fun default(
            http: Http = OkHttp(),
            keyStore: MarktguruSource.KeyStore = MarktguruSource.KeyStore.InMemory(),
        ) = Aggregator(
            listOf(
                MarktguruSource(http, keyStore),
                BillaShopSource(http),
                LidlSource(http),
                PennySource(http),
                SparShopSource(http),
            ),
            BillaFlyerText(http),
        )
    }
}
