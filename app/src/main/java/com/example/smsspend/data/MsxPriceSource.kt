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

    suspend fun fetch(symbol: String): Double? = withContext(Dispatchers.IO) {
        if (symbol.isBlank()) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(MARKET_WATCH).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json,*/*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            extractPrice(text, symbol)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Pure extraction: finds the [symbol] in the page and returns the first decimal number that
     * follows within a short window (the last-traded price). Unit-tested.
     */
    fun extractPrice(html: String, symbol: String): Double? {
        val idx = html.indexOf(symbol, ignoreCase = true)
        if (idx < 0) return null
        val window = html.substring(idx, minOf(html.length, idx + 500))
        val match = Regex("([0-9]+\\.[0-9]{2,4})").find(window) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }
}
