package com.example.smsspend.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Best-effort live price lookup from the Muscat Stock Exchange.
 *
 * MSX has no official public API and protects its site against non-browser clients, so this
 * is intentionally tolerant and fails soft (returns null) — the app always falls back to the
 * user's manually entered price. It is only used when the "Live MSX prices" setting is on.
 *
 * If MSX changes its market-watch page, only [MARKET_WATCH] and [extractPrice] need updating.
 */
object MsxPriceSource {

    private const val MARKET_WATCH = "https://www.msx.om/marketwatch.aspx"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36"

    // Candidate pages to try in order. MSX has no documented API, so this is best-effort.
    private val ENDPOINTS = listOf(
        MARKET_WATCH,
        "https://www.msx.om/Market.aspx",
        "https://msx.om/marketwatch.aspx"
    )

    suspend fun fetch(symbol: String): Double? = withContext(Dispatchers.IO) {
        if (symbol.isBlank()) return@withContext null
        for (endpoint in ENDPOINTS) {
            val price = runCatching { tryFetch(endpoint, symbol) }.getOrNull()
            if (price != null && price > 0.0) return@withContext price
        }
        null
    }

    private fun tryFetch(endpoint: String, symbol: String): Double? {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json,*/*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Referer", "https://www.msx.om/")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return extractPrice(text, symbol)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Pure extraction: scans every occurrence of [symbol] in the page, collects the decimal
     * numbers near each, and returns the first that looks like a real MSX share price
     * (a small OMR value). Falls back to the first decimal found. Unit-tested.
     */
    fun extractPrice(html: String, symbol: String): Double? {
        val sym = symbol.trim()
        if (sym.isEmpty()) return null
        val candidates = mutableListOf<Double>()
        var from = 0
        while (true) {
            val idx = html.indexOf(sym, from, ignoreCase = true)
            if (idx < 0) break
            val window = html.substring(idx, minOf(html.length, idx + 400))
            Regex("([0-9]+\\.[0-9]{2,4})").findAll(window).forEach { m ->
                m.groupValues[1].toDoubleOrNull()?.let { candidates.add(it) }
            }
            from = idx + sym.length
        }
        // MSX prices are small OMR figures; prefer a plausible one before any stray number.
        return candidates.firstOrNull { it in 0.01..100.0 } ?: candidates.firstOrNull()
    }
}
