# SMS Spend

An on-device Android app that reads your Bank Muscat SMS, categorizes spending,
and shows your money by **pay cycle** (anchored to your salary date, not the 1st of
the month). Built with Jetpack Compose + Material 3, a Room database, and home-screen
widgets. Everything stays on your phone — it only uses `READ_SMS` and makes no network
connections.

## Features

- **Dashboard** — Spent / Income / Net / Invested for the selected period, a
  tap-through category breakdown, and the full transaction list.
- **Flexible periods** — default is your **pay cycle**, anchored to your salary. The salary
  is detected as the *largest recurring monthly deposit* (so casual money people send you is
  never mistaken for it), and you can **pin the exact amount** in Settings. Each cycle starts
  on the real landing date (e.g. the 19th one month, the 23rd the next). Step backward/forward
  with ◀ ▶, or pick *This/Last pay cycle*, *This/Last month*, *This/Last year*, or any
  **custom date range**. A fixed fallback day is used until enough salary deposits are detected.
- **Insights & predictions** — balance, spend-per-day, projected spend for the cycle,
  runway (how long your balance lasts at this pace), largest transaction, busiest day, and a
  trend vs. the previous cycle.
- **Bank balance from SMS** — your running balance is read straight from bank messages
  ("رصيدك الحالي هو …") into a balance-over-time series; no typing needed (with a manual
  override available).
- **Smart categorization that learns** — re-categorize a merchant once and **every**
  transaction from that merchant updates (fixing one TALABAT fixes them all). Merchant
  names are cleaned up automatically.
- **Granular merchant view** — top merchants per period, a per-merchant breakdown inside each
  category (Talabat vs. a specific restaurant), and total / count / average / first & last
  seen for any merchant with its full history.
- **IPO & dividends** — IPO subscriptions are tracked as *Investments*, dividend payouts
  as *Dividends*. A Settings toggle decides whether IPO buys count toward "Spent".
- **Investments / portfolio** — a dedicated screen for your stocks: enter shares + price
  per holding to see market value, with dividends auto-tallied per company (bank + MCD
  messages), your IPO subscriptions, and AGM dates auto-discovered from SMS. Optionally
  fetch live prices from MSX (see below).
- **Export & copy** — copy the current view as text, or export everything (transactions,
  categories, holdings, balances, settings) as JSON to share or feed back for analysis.
- **Resizable widgets** — a compact metric widget (Spent/Income/Net/Invested/**Balance**/
  **Per-day**/**Projected**) and a metric + top-categories widget that also shows a **trend
  line**, each with a config screen to choose what it shows.
- **Your data survives** — hand-entered values (salary, balance, holdings, learned
  categories) persist across app updates; database changes use real migrations, never a wipe.

## MSX prices & network

The app is on-device by default. The Investments screen works fully offline with
manually entered prices. If you turn on **Settings → Live MSX prices**, the app adds the
`INTERNET` permission's usage to fetch quotes from `msx.om` by symbol. MSX has no official
API and blocks non-browser clients, so live fetch is **best-effort** and falls back to your
manual price whenever it can't reach a quote. SMS data never leaves the device either way.

## Build the APK (from a phone browser, no computer needed)

1. Push this repository to GitHub (it already contains a Gradle project + workflow).
2. Open the **Actions** tab — the **Build APK** workflow runs on every push, and can also
   be started manually with **Run workflow**.
3. Wait for the green check, open the run, and download the **SmsSpend-apk** artifact.
   Extract it to get `app-debug.apk`.

## Install

1. Open the APK from Files/Downloads and allow installing from this source.
2. Launch the app and tap **Allow** for SMS access.
3. Tap **Refresh** to import. Long-press the home screen → **Widgets** → **SMS Spend**.

## Project layout

```
app/src/main/java/com/example/smsspend/
  parser/      SmsParser, Categorizer        (pure Kotlin, unit-tested)
  model/       Period / Periods, Totals       (pure Kotlin, unit-tested)
  data/        Room entities, DAOs, Repository, Prefs, SmsReader
  ui/          Compose screens + ViewModel
  widget/      Widget providers + config activity
app/src/test/  JVM unit tests for the parser, categorizer, periods, totals
```

## Notes for developers

- The SMS parser is deliberately isolated and **covered by unit tests**
  (`SmsParserTest`). The documented message patterns live in `SmsParser.kt`; never change
  a regex without updating/adding a test.
- Storage is a Room database (`smsspend.db`); learned per-merchant rules live in
  `merchant_rule`.
- Permissions: `READ_SMS` (always) and `INTERNET` (used only when Live MSX prices is on).
  Ask the owner before adding any further permission or dependency.
