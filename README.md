# RabattRadar

Android app that collects the current offers of **BILLA (incl. BILLA PLUS), SPAR (SPAR, EUROSPAR, INTERSPAR), HOFER, Lidl and PENNY** in one searchable list. It shows every caveat attached to a price: jö / Lidl Plus / SPAR App, 2+1 gratis, "ab 2 Stk. je", Fr/Sa-only deals, deposit, online-only, validity dates and more.

Every offer picture carries a **flyer-style info bar in the bottom-left corner**, the same layout as the printed flyers:

```
┌──────────────────┐
│ 1 TAFEL €4.99    │  reference price: single unit, without app, or crossed-out price
│   2+1 GRATIS     │  deal: 2+1 GRATIS · -45% · LIDL PLUS · jö PREIS · APP-GUTSCHEIN
│ BEI 3 TAFELN JE  │  condition: BEI 3 … JE · AB 2 PKG. JE · NUR FR & SA · MIT jö
│       3³²        │  price, with the cents set as superscript
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
- **Every offer has a picture.** If none is available, or it fails to load, a generated tile in the chain colours is shown instead.
- Works offline from the last snapshot. It refreshes automatically when the data is older than 4 h, and on pull-to-refresh.
- The postal code setting selects regional flyers (e.g. BILLA Wien vs. other states).

## Data sources

Scraping runs **on the phone**, so no server is needed and the requests come from a normal residential IP. All endpoints are unofficial and undocumented.

| Chain | Source | What it provides |
|---|---|---|
| BILLA / BILLA PLUS | `shop.billa.at/api/product-discovery/products?inPromotion=true` | ~1,100 promotions with structured multi-buy (`promotionText: "Bei 3 Tafeln je"`, `PER_SET_OF`/`FROM`, tags `pt-2plus1`), deposit, lowest 30-day price. **No dates.** |
| | marktguru flyer offers | Dates and BILLA PLUS-only items. Merged into the shop offers (see below). |
| SPAR group | marktguru flyer offers | The weekly SPAR/EUROSPAR/INTERSPAR/Gourmet flyer, including the `MIT SPAR-APP-GUTSCHEIN 2,15 €` hint |
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
- [supermarket-deals-at](https://github.com/roehrbacher/supermarket-deals-at) (aktionsradar.at + Lidl/BILLA APIs) and [Nightdreams-bat/supermarket-deals](https://github.com/Nightdreams-bat/supermarket-deals) (marktguru) confirmed the endpoints and the "query the banner name to get the whole flyer" trick.

A live run (27 Oct 2026, PLZ 1010) gave about **3,850 merged offers**, all with a picture:

| Source | Offers |
|---|---|
| marktguru | 2,456 |
| BILLA shop | 1,145 |
| lidl.at | 349 |
| penny.at | 483 |
| INTERSPAR shop | 460 |

### Known limitations

- **BILLA**: promotions found only in the online shop and not in the flyer have no end date. The app labels them "Datum unbekannt" and "lt. Online-Shop". Flyer-only items have no multi-buy details unless they can be inferred.
- **HOFER and SPAR flyer items** rely on marktguru alone, so multi-buy conditions may be missing; the ⅔ heuristic only catches 2+1. Personalised jö coupons and the "Rabattsammler" are not covered, since they live inside the jö/BILLA apps behind a login.
- Logos are loaded from marktguru's CDN and cached. While they load, or when offline before the first load, a drawn word mark in the chain colours is shown.
- The endpoints can change at any time. When a source fails, the others still load and the app shows which one is missing.

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
