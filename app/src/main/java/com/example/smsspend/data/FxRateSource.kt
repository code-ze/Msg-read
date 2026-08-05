package com.example.smsspend.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live mid-market exchange rates, used only to price currencies the rial's peg can't reach on
 * its own (the euro, and anything floating).
 *
 * Two free, key-less providers are tried in order so one being down isn't fatal. Both are asked
 * for USD as the base and normalised to the same shape: **units of X per 1 USD**.
 *
 * Fails soft: returns null and the caller keeps using the last cached set, however old. A stale
 * mid-market rate still gives a far better markup estimate than no rate, and these are reference
 * rates that move by fractions of a percent day to day.
 */
object FxRateSource {

    private val ENDPOINTS = listOf(
        "https://api.frankfurter.dev/v1/latest?base=USD",
        "https://open.er-api.com/v6/latest/USD"
    )

    /** Fetched rates as "units of X per 1 USD", plus when they were retrieved. */
    data class Snapshot(val perUsd: Map<String, Double>, val fetchedAt: Long)

    suspend fun fetch(): Snapshot? = withContext(Dispatchers.IO) {
        for (endpoint in ENDPOINTS) {
            val rates = runCatching { tryFetch(endpoint) }.getOrNull()
            if (!rates.isNullOrEmpty()) {
                return@withContext Snapshot(rates, System.currentTimeMillis())
            }
        }
        null
    }

    private fun tryFetch(endpoint: String): Map<String, Double>? {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            return parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Pure parsing of either provider's payload — both nest the numbers under "rates", so one
     * reader covers them. Unit-tested against real response shapes.
     */
    fun parse(json: String): Map<String, Double>? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val rates = obj.optJSONObject("rates") ?: return null
        // Guard against a provider quietly switching base currency on us.
        val base = obj.optString("base", "USD").uppercase()
        if (base.isNotEmpty() && base != "USD") return null

        val out = HashMap<String, Double>()
        val keys = rates.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = rates.optDouble(k, Double.NaN)
            if (!v.isNaN() && v > 0.0) out[k.uppercase()] = v
        }
        return out.ifEmpty { null }
    }
}
