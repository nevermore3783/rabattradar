package at.rabattradar.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

/** Supermarket chain. Sub-banners (BILLA PLUS, INTERSPAR, ...) are kept in [Offer.banners]. */
@Serializable
enum class Chain(val displayName: String, val website: String) {
    BILLA("BILLA", "https://www.billa.at/unsere-aktionen/aktionen"),
    SPAR("SPAR", "https://www.spar.at/angebote"),
    HOFER("HOFER", "https://www.hofer.at/de/angebote.html"),
    LIDL("Lidl", "https://www.lidl.at/c/filial-angebote/a10023711"),
    PENNY("PENNY", "https://www.penny.at/angebote"),
}

/** Where an offer was scraped from. One merged offer can come from several sources. */
@Serializable
enum class SourceId(val displayName: String, val description: String) {
    MARKTGURU("marktguru.at", "Flugblatt-Angebote (alle Ketten, inkl. HOFER)"),
    BILLA_SHOP("BILLA Online-Shop", "Strukturierte Aktionen inkl. Mengenrabatte, aber ohne Gültigkeitsdatum"),
    LIDL_WEB("lidl.at", "Filial-Angebote mit Lidl Plus Preisen und exakten Aktionstagen"),
    PENNY_WEB("penny.at", "Wochenangebote mit jö/App-Preisen und Mengenrabatten"),
    SPAR_SHOP("INTERSPAR Online-Shop", "Online-Shop-Aktionen (meist Non-Food und Getränke)"),
}

@Serializable
enum class ConditionType {
    /** jö Bonus Club card (BILLA, PENNY). */
    LOYALTY_CARD,

    /** Price only with a loyalty app (Lidl Plus, PENNY App). */
    LOYALTY_APP,

    /** Voucher that must be activated in an app (SPAR App). */
    APP_COUPON,

    /** "2+1 gratis", "1+1 gratis", ... */
    MULTI_BUY,

    /** "ab 2 Stk. je", "bei 3 Tafeln je" - price only applies from a minimum quantity. */
    MIN_QUANTITY,

    /** Offer only valid on certain weekdays ("nur Fr & Sa"). */
    WEEKDAYS,

    /** Only available online. */
    ONLINE_ONLY,

    /** Data comes from an online shop - in-store prices can differ. */
    ONLINE_SHOP,

    /** Clearance / "Abverkauf". */
    CLEARANCE,

    /** Price per kg - final price depends on weight. */
    WEIGHT_PRICE,

    /** Deposit (Pfand) is added at the till. */
    DEPOSIT,

    /** Several variants share the price ("versch. Sorten"). */
    VARIANTS,

    /** The source does not say how long the offer runs. */
    NO_DATES,

    /** Anything else worth knowing. */
    NOTE,
}

@Serializable
data class Condition(
    val type: ConditionType,
    /** Short chip label, e.g. "Lidl Plus", "2+1 gratis", "ab 2 Stk.". */
    val label: String,
    /** Longer explanation for the detail screen. */
    val detail: String? = null,
    /** True when derived heuristically rather than stated by the source. */
    val inferred: Boolean = false,
    /** Minimum quantity for MIN_QUANTITY / MULTI_BUY conditions. */
    val quantity: Int? = null,
    /** Singular unit word for MIN_QUANTITY ("Tafel", "Pkg."). */
    val unit: String? = null,
)

@Serializable
enum class DepositKind { SINGLE_USE, REUSABLE }

/** Deposit (Pfand) charged on top of the advertised price. */
@Serializable
data class Deposit(
    val kind: DepositKind,
    /** Total deposit for one advertised unit (a 6-pack -> 6 x 0.25), null if unknown (reusable crates). */
    val amount: Double?,
    /** Number of bottles/cans in one advertised unit. */
    val containers: Int = 1,
    /** Derived from drink type, container and volume rather than stated by the source. */
    val inferred: Boolean = false,
)

/** Alternative price shown next to the headline price, e.g. "1 Tafel 4.99" or "ohne Lidl Plus 1.89". */
@Serializable
data class PriceTier(val label: String, val price: Double)

@Serializable
data class Offer(
    /** Stable id, unique within a snapshot. */
    val id: String,
    val chain: Chain,
    /** Store banners the offer is valid at, e.g. ["BILLA", "BILLA PLUS"]. */
    val banners: List<String> = emptyList(),
    val name: String,
    val brand: String? = null,
    /** Variant / description text ("versch. Sorten", "Klasse I"). */
    val description: String? = null,
    /** Package size ("300 g", "Je 20x 0,5 l"). */
    val quantity: String? = null,
    val category: String? = null,
    /** Headline price as advertised - may require the conditions below. */
    val price: Double,
    /** Normal / crossed-out price, if known. */
    val regularPrice: Double? = null,
    /** Alternative prices (single unit, without app, lowest 30-day price ...). */
    val tiers: List<PriceTier> = emptyList(),
    val discountPercent: Int? = null,
    /** Base price text, e.g. "1 kg = 5.79". */
    val unitPrice: String? = null,
    val conditions: List<Condition> = emptyList(),
    val deposit: Deposit? = null,
    @Serializable(with = LocalDateSerializer::class) val validFrom: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val validTo: LocalDate? = null,
    /** Best image for the product (packshot if available, else flyer crop). */
    val imageUrl: String? = null,
    /** Crop from the printed flyer, if a source provides one. */
    val flyerImageUrl: String? = null,
    val url: String? = null,
    val sources: List<SourceId> = emptyList(),
) {
    /** Brand to show above the name - omitted when the name already starts with it ("Milka Alpenmilch"). */
    val displayBrand: String?
        get() = brand?.takeUnless { name.lowercase().startsWith(it.lowercase()) }

    val title: String
        get() = listOfNotNull(displayBrand, name).joinToString(" ")

    fun has(type: ConditionType) = conditions.any { it.type == type }

    /** Advertised price plus deposit, if a deposit amount is known. */
    val priceWithDeposit: Double?
        get() = deposit?.amount?.let { price + it }

    /** Sets the deposit and keeps the DEPOSIT condition in sync with it. */
    fun withDeposit(d: Deposit?): Offer = copy(
        deposit = d,
        conditions = conditions.filterNot { it.type == ConditionType.DEPOSIT } +
            listOfNotNull(d?.let { at.rabattradar.core.parse.Deposits.condition(it, price) }),
    )

    fun condition(type: ConditionType) = conditions.firstOrNull { it.type == type }

    /** True if the advertised price needs a card, app or voucher. */
    val needsMembership: Boolean
        get() = conditions.any {
            it.type == ConditionType.LOYALTY_CARD || it.type == ConditionType.LOYALTY_APP || it.type == ConditionType.APP_COUPON
        }

    /** True if the price needs more than one item. */
    val needsMultiple: Boolean
        get() = conditions.any {
            (it.type == ConditionType.MULTI_BUY || it.type == ConditionType.MIN_QUANTITY) && !it.inferred
        }

    fun isActiveOn(date: LocalDate): Boolean =
        (validFrom == null || !validFrom.isAfter(date)) && (validTo == null || !validTo.isBefore(date))

    fun isUpcoming(date: LocalDate): Boolean = validFrom != null && validFrom.isAfter(date)

    fun isExpired(date: LocalDate): Boolean = validTo != null && validTo.isBefore(date)
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
