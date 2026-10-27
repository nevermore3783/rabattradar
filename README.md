# RabattRadar

Android app that collects the current offers of **BILLA (incl. BILLA PLUS), SPAR (SPAR, EUROSPAR, INTERSPAR), HOFER, Lidl and PENNY** in one searchable list. It shows every caveat attached to a price: jö / Lidl Plus / SPAR App, 2+1 gratis, "ab 2 Stk. je", Fr/Sa-only deals, deposit, online-only, validity dates and more.

Every offer picture carries a **flyer-style info bar in the bottom-left corner**, the same layout as the printed flyers:

```
┌──────────────────┐
│ 1 TAFEL €4.99    │  reference price: single unit, without app, or crossed-out price
│   2+1 GRATIS     │  deal: 2+1 GRATIS · -45% · LIDL PLUS · jö PREIS · APP-GUTSCHEIN
│ BEI 3 TAFELN JE  │  condition: BEI 3 … JE · AB 2 PKG. JE · NUR FR & SA · MIT jö
│  3³²  +€0.25 PFAND│  price (cents as superscript); deposit and price incl. deposit in small print beside it
│       = €3.57    │
│ 3 TAFELN = €9.96 │  package price for multi-buys (2+1, 3+3, "ab 24 Dosen je")
└──────────────────┘
```

The colours follow each chain's flyers. Lidl Plus and SPAR App prices get a yellow "member price" look.

## Features

- **Search across all chains** with umlaut folding (`kaese` finds *Käse*). Substring matching handles compounds (`schoko` finds *Milchschokolade*). The search also covers condition labels, so `lidl plus` or `jö` work too.
- **Store chips with logos** and counts per chain. Tabs for **Jetzt gültig / Demnächst / Alle**, plus sorting by relevance, discount, price or end date.
- **Filters**: "Ohne Kundenkarte & App" (hides jö/Lidl Plus/App prices), "Nur Einzelpreise" (hides multi-buys), online-shop offers on/off, and a watchlist (♥).
- **Detail screen** with:
  - all price tiers (single price, price without app, lowest price of the last 30 days)
  - each condition with an explanation
  - validity with weekdays and days left
  - the original flyer crop
  - the data sources, plus share and "open at store" buttons
- **Package price** for multi-buys: "bei 3 Tafeln je 3.32" shows "3 Tafeln = 9.96 €", and 3+3 shows the price for 6 items. The detail screen adds the deposit and the total at the till ("18,96 € + 6,00 € Pfand = 24,96 €").
- **Deposit (Pfand)** is added in small print next to the price ("+0,25 € Pfand = 1,04 €") for every eligible item. Multipacks count every container (6 × 0,25 €). See [Deposit rules](#deposit-rules).
- **Every offer has a picture.** If none is available, or it fails to load, a generated tile in the chain colours is shown instead.
- **German and English.** Switch under Settings → Sprache / Language (or follow the system language). All texts the app generates are translated: conditions, info bar, dates and explanations. Product names and descriptions come from the shops and stay German.
- Works offline from the last snapshot. It refreshes automatically when the data is older than 4 h, and on pull-to-refresh.
- The postal code setting selects regional flyers (e.g. BILLA Wien vs. other states).

## Data sources

Scraping runs **on the phone**, so no server is needed and the requests come from a normal residential IP. All endpoints are unofficial and undocumented.

| Chain | Source | What it provides |
|---|---|---|
| BILLA / BILLA PLUS | `shop.billa.at/api/product-discovery/products?inPromotion=true` | ~1,100 promotions with structured multi-buy (`promotionText: "Bei 3 Tafeln je"`, `PER_SET_OF`/`FROM`, tags `pt-2plus1`), deposit, lowest 30-day price. **No dates.** |
| | marktguru flyer offers | Dates and BILLA PLUS-only items. Merged into the shop offers (see below). |
| | BILLA/BILLA PLUS flyer text (Publitas `spreads.json`) | Multi-buys that marktguru drops ("EXTREM AKTION 3+3 GRATIS, BEI 6 FL. JE"). See below. |
| SPAR group | marktguru flyer offers | The weekly SPAR/EUROSPAR/INTERSPAR/Gourmet flyer, including the `MIT SPAR-APP-GUTSCHEIN 2,15 €` hint |
| | SPAR/EUROSPAR/Gourmet/INTERSPAR flyer text (iPaper flipbooks on `flugblatt.spar.at` / `flugblatt.interspar.at`) | Multi-buys and tray prices that marktguru drops ("1 Ds. 1,39 ab 24 Ds. je 0,69 12+12 GRATIS 24er-Tray 16,56"). See below. |
| | INTERSPAR shop search (`search-spar.spar-ics.com`, `is-on-promotion:true`) | Online-shop promotions (mostly wine, drinks and household), flagged as such |
| HOFER | marktguru flyer offers | The full weekly flyer, including Fr/Sa specials |
| Lidl | `lidl.at/q/api/search?q=*` | All current and upcoming in-store promotions with exact start/end dates, **Lidl Plus prices** (including Lidl Plus exclusive deals), "Bei 2 Stk. je", single price |
| | marktguru flyer offers | Extra flyer items |
| PENNY | `penny.at/api/product-discovery/products` | All weekly offers with dates, **jö/App prices** (`price.loyalty`), multi-buys, "Wochenend-Kick" / "Wochenstarter" campaigns |
| | marktguru flyer offers | `MIT PENNY APP + JÖ KARTE` hints, extra items |

### Why this mix (and what existing projects miss)

I looked at existing projects before writing the scrapers:

- **[heissepreise](https://github.com/badlogic/heissepreise)**: product *catalogue* tracking, not flyer offers. Its PENNY API was dead (`/api/products` returns 404 since Oct 2025). The working PENNY endpoint is now `/api/product-discovery/products`, the same backend as BILLA. Its HOFER source (roksh.at) no longer exists.
- **preisrunter.at and similar price comparisons** are built on **online-shop catalogues**. That is why their Lidl and HOFER coverage is incomplete:
  - HOFER has no grocery online shop, and `hofer.at` sits behind Akamai bot protection (HTTP 403 for any non-browser client).
  - Lidl's website only lists part of the food range.
  - Discounter deals mostly exist **only in the printed flyer**, so a catalogue-based tracker cannot see them.
- **Flyer aggregators** cover all chains. aktionsfinder.at has been offline ("bis auf Weiteres außer Betrieb") since 31 May 2026; marktguru.at works, and its keys are scraped from its homepage at runtime. But marktguru **drops conditions**: the SPAR flyer "1 Pkg. 0,99 – bei 3 Pkg. je 0,66 – 2+1 GRATIS" becomes plain `price 0.66, oldPrice 0.99`. The app counters this in two ways:
  1. **Merging with shop data.** The structured shop offer (BILLA/Lidl/PENNY) wins, and the flyer adds dates, banners and the flyer crop. Matching uses chain, exact price, name tokens and overlapping dates. BILLA's brand-wide promotions ("Milka div. Sorten") pass their flyer dates on to all shop SKUs with the same promotion signature.
  2. **Inference with a visible "?"**. A flyer price of exactly ⅔ of the normal price is shown as **"2+1 GRATIS?"** with an explanation, never as a confirmed condition. A REWE `PER_SET_OF` price that equals 2 of 3 single prices is shown as "2+1 gratis", because that follows exactly from the numbers.
- **BILLA flyer text.** BILLA and BILLA PLUS host their flyers on Publitas, whose `spreads.json` carries the extracted page text. That text contains every element of the price tag, but two-column layouts interleave neighbouring products ("1 FL. € 2.49 · 1 FL. € 1.99 · 4+2 · 3+3 · BEI 6 FL. JE · BEI 6 FL. JE"), so text order alone is not enough. For each flyer offer the app:
  1. finds its name on the page;
  2. takes the "1 FL. € 2.49" line equal to its normal price;
  3. picks the X+Y label that is arithmetically consistent (1.24 × 6 = 2.49 × 3 → 3+3, not 4+2).

  Price lines and labels are assigned once per page in reading order, so two Sekt offers take the two "1+1" labels and the Eistee next to them gets its "2+2". This recovers about 60 BILLA conditions per week. HOFER and Lidl flyers are also on Publitas (Promozilla), but as images without text.
- **SPAR flyer text.** spar.at and interspar.at sit behind Cloudflare, but the flyers themselves are iPaper flipbooks on `flugblatt.spar.at` and `flugblatt.interspar.at`, which are not. Every flipbook page embeds its text (`window.staticSettings.pageTexts`). The addresses are predictable from the region (derived from the postal code) and the flyer's first day, which the marktguru offer dates provide:
  - `flugblatt.spar.at/{region}/{spar|eurospar}/{yyMMdd}-1-flugblatt-kw-{week}/`
  - `flugblatt.spar.at/{region}/spar-gourmet/{yyMMdd}-1-flugblatt-kw-{week}-{week+1}/`
  - `flugblatt.interspar.at/{region}/{region}_kw{week}/`

  A page's text is a single run of words with neighbouring products interleaved ("1 Fl. 1,99 ab 6 Fl. je Coca-Cola 1,32 4 + 2 GRATIS"), so matching works on character positions. A price block "1 Ds. 1,39 ab 24 Ds. je" must carry the offer's normal price, the offer's brand or name must appear close to it, and the "X+Y" label must fit the prices (0.69 × 24 = 1.39 × 12 → 12+12). A printed tray price that equals quantity × price ("6er-Tray 7,92", "2 x 12er-Tray 15,36") becomes the package price. If the headline is a SPAR-app voucher price, a multi-buy that only fits the price without the voucher is kept as a note ("ohne Gutschein: 4+2 gratis"). A guessed "2+1?" that the flyer prints as a plain reduction ("statt 7,49 4,99") is removed. This recovers about 165 SPAR-group conditions per week (Vienna, KW 43/2026).
- [supermarket-deals-at](https://github.com/roehrbacher/supermarket-deals-at) (aktionsradar.at + Lidl/BILLA APIs) and [Nightdreams-bat/supermarket-deals](https://github.com/Nightdreams-bat/supermarket-deals) (marktguru) confirmed the endpoints and the "query the banner name to get the whole flyer" trick.

A live run (27 Oct 2026, PLZ 1010) gave about **3,850 merged offers**, all with a picture:

| Source | Offers |
|---|---|
| marktguru | 2,456 |
| BILLA shop | 1,145 |
| lidl.at | 349 |
| penny.at | 483 |
| INTERSPAR shop | 460 |
| BILLA flyer text (conditions added) | 62 |
| SPAR flyer text (conditions added) | 167 |

### Known limitations

- **BILLA**: promotions found only in the online shop and not in the flyer have no end date. The app labels them "Datum unbekannt" and "lt. Online-Shop". Flyer-only items have no multi-buy details unless they can be inferred.
- **HOFER flyer items** rely on marktguru alone, so multi-buy conditions may be missing; the ⅔ heuristic only catches 2+1 (or 4+2). For SPAR, only the main weekly flyers are read; offers from extra flyers (Halloween, "Monatssparer") keep the marktguru data. Personalised jö coupons and the "Rabattsammler" are not covered, since they live inside the jö/BILLA apps behind a login.
- Logos are loaded from marktguru's CDN and cached. While they load, or when offline before the first load, a drawn word mark in the chain colours is shown.
- The endpoints can change at any time. When a source fails, the others still load and the app shows which one is missing.

## Deposit rules

These follow the Austrian Pfandverordnung (since 1 Jan 2025):

- **€0.25 per plastic bottle or metal can** holding 0.1–3 l.
- **Exempt**: milk and dairy drinks, beverage cartons (Tetra Pak, Elopak), glass one-way bottles, and containers under 0.1 l or over 3 l.
- **Mehrweg** (reusable glass and crates) has product-specific amounts.

| Signal | Example | Result |
|---|---|---|
| Shop field | BILLA `depositPrice` + `isdeposittype-*` | Exact amount (e.g. 1.20 € for a 6-pack of Mehrweg bottles) |
| Flyer text | "zzgl. Pfand 8 x € 0,25 = € 2", "zzgl. Pfand € 0,25", "Preis exkl. Pfand", "EINWEG mit Pfand" | Stated amount |
| Flyer text | "Pfandfrei", "EINWEG ohne Pfand" | No deposit |
| Shop field | PENNY `depositType: ONEWAY/REUSABLE` | Amount from the rules + container (cans yes, glass beer no) |
| Nothing stated | Soft drink, water or juice with volume, can/PET or no container; single 0.5 l beer | Inferred, shown with "?" |
| Mehrweg without amount | "20 x 0,5-Liter-Mehrweg-Flasche" | "+ Mehrwegpfand" (amount unknown) |

BILLA lists every deposit explicitly, so a BILLA shop item without one is trusted to have none; its 1 l Tetra Pak juices and glass bottled beer show no deposit, which is correct. Food containers ("Vorratsdose 1,5 l"), wine (grape names, vintages) and non-food ("Wasserkocher 1,7 l") are excluded.

## Project layout

```
core/  pure Kotlin/JVM: scrapers, condition parser, merger, search (unit tested with real API fixtures)
cli/   command-line runner for a live scrape without a phone
app/   Android app (Jetpack Compose, Material 3, Coil, DataStore)
```

- `core/.../source/` contains one class per source: `MarktguruSource`, `BillaShopSource`, `PennySource`, `LidlSource`, `SparShopSource`.
- `core/.../parse/Conditions.kt` turns German flyer texts into typed conditions: `LOYALTY_CARD`, `LOYALTY_APP`, `APP_COUPON`, `MULTI_BUY`, `MIN_QUANTITY`, `WEEKDAYS`, `ONLINE_ONLY`, `CLEARANCE`, `WEIGHT_PRICE`, `DEPOSIT`, `VARIANTS`, `NO_DATES`, …
- `core/.../model/InfoBar.kt` decides what goes into the flyer-style tag; `app/.../ui/components/InfoBarTag.kt` draws it.

## Build & run

Requirements: JDK 17+ and the Android SDK (compileSdk 36).

```sh
./gradlew :core:test                 # unit tests
./gradlew :app:assembleRelease       # app/build/outputs/apk/release/app-release.apk (R8-shrunk, ~2 MB)
./gradlew :cli:run --args="--zip 1010 --search milka --out /tmp/snapshot.json"   # live scrape on the desktop
```

The release APK is signed with the debug key so it can be side-loaded directly. GitHub Actions builds it on every push; download it from the run's artifacts. CI uses a fresh debug key per run, so uninstall the previous version before installing a new CI build.

### Screenshots without an emulator

Robolectric and Roborazzi render the Compose UI on the JVM:

```sh
./gradlew :app:recordRoborazziDebug --tests '*ScreenshotTest*'                                   # sample offers
./gradlew :app:recordRoborazziDebug --tests '*ScreenshotTest*' -Drabattradar.snapshot=/tmp/snapshot.json   # real data
# -> app/build/outputs/roborazzi/*.png
```

## Disclaimer

This is a personal project with no connection to the retailers. Brands and logos belong to their owners, and all data is provided without guarantee. Keep the request rate low: the app refreshes at most every 4 hours unless you refresh manually.

The flyer font is "Barlow Condensed", SIL Open Font License 1.1 (`app/src/main/assets/licenses`).
