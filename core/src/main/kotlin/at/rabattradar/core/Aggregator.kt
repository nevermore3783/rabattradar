package at.rabattradar.core

import at.rabattradar.core.merge.OfferMerger
import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.SourceId
import at.rabattradar.core.net.Http
import at.rabattradar.core.net.OkHttp
import at.rabattradar.core.source.BillaShopSource
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
class Aggregator(private val sources: List<OfferSource>) {

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
        val results = jobs.awaitAll()
        val all = results.flatMap { it.first }.filter { it.chain in chains && !it.isExpired(ctx.today) }
        Snapshot(
            fetchedAt = System.currentTimeMillis(),
            zipCode = ctx.zipCode,
            offers = OfferMerger.merge(all),
            statuses = results.map { it.second },
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
        )
    }
}
