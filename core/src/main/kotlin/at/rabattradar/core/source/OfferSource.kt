package at.rabattradar.core.source

import at.rabattradar.core.model.Chain
import at.rabattradar.core.model.Offer
import at.rabattradar.core.model.SourceId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FetchContext(
    /** Austrian postal code - marktguru returns regional flyers based on it. */
    val zipCode: String = "1010",
    val today: LocalDate = LocalDate.now(VIENNA),
)

interface OfferSource {
    val id: SourceId
    val chains: Set<Chain>
    suspend fun fetch(ctx: FetchContext): List<Offer>
}

val VIENNA: ZoneId = ZoneId.of("Europe/Vienna")

internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun epochSecondsToDate(seconds: Long): LocalDate = Instant.ofEpochSecond(seconds).atZone(VIENNA).toLocalDate()

internal fun isoInstantToDate(value: String): LocalDate? = runCatching {
    Instant.parse(value).atZone(VIENNA).toLocalDate()
}.getOrNull() ?: runCatching { LocalDate.parse(value.take(10)) }.getOrNull()

// Small JSON accessors - the APIs are undocumented and fields come and go, so everything is optional.
internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)?.takeUnless { it is JsonNull }
internal operator fun JsonElement?.get(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)
internal val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString || it.toString() != "null" }?.content
internal val JsonElement?.dbl: Double? get() = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.replace(',', '.').toDoubleOrNull() }
internal val JsonElement?.lng: Long? get() = (this as? JsonPrimitive)?.longOrNull
internal val JsonElement?.bool: Boolean? get() = (this as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.toBooleanStrictOrNull() }
internal val JsonElement?.arr: List<JsonElement> get() = (this as? JsonArray) ?: emptyList()
