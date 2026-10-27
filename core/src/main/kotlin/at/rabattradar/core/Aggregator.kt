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
import at.rabattradar.core.source.SparFlyerText
import at.rabattradar.core.source.SparShopSource
import kotlinx.coroutines.Deferred
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
    private val sparFlyer: SparFlyerText? = null,
) {

    /** Flyer text that adds conditions to the merged offers; loading its pages fails as a whole. */
    private class FlyerJob(
        val id: SourceId,
        val result: Deferred<FlyerResult>,
        val enrich: (List<Offer>, List<FlyerPage>) -> List<Offer>,
    )

    private class FlyerResult(val pages: List<FlyerPage>, val error: String?, val millis: Long)

    suspend fun fetch(
        ctx: FetchContext,
        chains: Set<Chain> = Chain.entries.toSet(),
        enabledSources: Set<SourceId> = SourceId.entries.toSet(),
    ): Snapshot = coroutineScope {
        val jobs = sources
            .filter { it.id in enabledSources && it.chains.any { c -> c in chains } }
            .associateWith { source ->
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

        fun flyerJob(id: SourceId, chain: Chain, enrich: (List<Offer>, List<FlyerPage>) -> List<Offer>, pages: suspend () -> List<FlyerPage>) =
            if (chain in chains && id in enabledSources) {
                FlyerJob(id, async {
                    val start = System.currentTimeMillis()
                    try {
                        FlyerResult(pages(), null, System.currentTimeMillis() - start)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        FlyerResult(emptyList(), e.message ?: e.javaClass.simpleName, System.currentTimeMillis() - start)
                    }
                }, enrich)
            } else null

        // The SPAR flyer addresses contain the start date, which the marktguru offers tell.
        val marktguru = jobs.entries.firstOrNull { it.key is MarktguruSource }?.value
        val flyerJobs = listOfNotNull(
            billaFlyer?.let { flyer -> flyerJob(flyer.id, Chain.BILLA, BillaFlyerText::enrichAll) { flyer.pages(ctx.today) } },
            sparFlyer?.let { flyer ->
                flyerJob(flyer.id, Chain.SPAR, SparFlyerText::enrichAll) {
                    val starts = marktguru?.await()?.first.orEmpty().filter { it.chain == Chain.SPAR }.mapNotNull { it.validFrom }
                    flyer.pages(ctx.zipCode, ctx.today, starts)
                }
            },
        )

        val results = jobs.values.awaitAll()
        val all = results.flatMap { it.first }.filter { it.chain in chains && !it.isExpired(ctx.today) }
        var offers = OfferMerger.merge(all)
        val statuses = results.map { it.second }.toMutableList()

        for (job in flyerJobs) {
            val r = job.result.await()
            if (r.error != null) {
                statuses += SourceStatus(job.id, false, 0, r.error, r.millis)
                continue
            }
            val before = offers
            offers = job.enrich(offers, r.pages)
            val enriched = offers.indices.count { offers[it] !== before[it] }
            statuses += SourceStatus(job.id, r.pages.isNotEmpty(), enriched, if (r.pages.isEmpty()) "Kein Flugblatt-Text gefunden" else null, r.millis)
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
            SparFlyerText(http),
        )
    }
}
