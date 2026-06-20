package com.example.smsspend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class StockPrice(
    val symbol: String,
    val price: Double,
    val change: Double,
    val changePct: Double,
    val volume: Long = 0L
)

object MsxService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cache = HashMap<String, Pair<Long, StockPrice>>()
    private const val CACHE_TTL_MS = 15 * 60 * 1000L

    suspend fun getPrice(symbol: String): Result<StockPrice> = withContext(Dispatchers.IO) {
        val cached = cache[symbol.uppercase()]
        if (cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
            return@withContext Result.success(cached.second)
        }
        try {
            val price = tryMsxApi(symbol) ?: tryMsxHtml(symbol)
                ?: return@withContext Result.failure(Exception("Price not available for $symbol"))
            cache[symbol.uppercase()] = System.currentTimeMillis() to price
            Result.success(price)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun tryMsxApi(symbol: String): StockPrice? {
        return try {
            val url = "https://www.msx.gov.om/en-us/market/stocks/equity/GetStockDetails?stockCode=${symbol.uppercase()}"
            val req = Request.Builder().url(url)
                .addHeader("Accept", "application/json")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()
            val body = client.newCall(req).execute().use { it.body?.string() } ?: return null
            parseJsonPrice(symbol, body)
        } catch (e: Exception) { null }
    }

    private fun tryMsxHtml(symbol: String): StockPrice? {
        return try {
            val url = "https://www.msx.gov.om/en-us/market/stocks/equity/stockdetails?stockCode=${symbol.uppercase()}"
            val req = Request.Builder().url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Android)")
                .build()
            val body = client.newCall(req).execute().use { it.body?.string() } ?: return null
            parseHtmlPrice(symbol, body)
        } catch (e: Exception) { null }
    }

    private fun parseJsonPrice(symbol: String, json: String): StockPrice? {
        return try {
            val obj = JSONObject(json)
            val price = obj.optDouble("LastPrice").takeIf { !it.isNaN() }
                ?: obj.optDouble("lastPrice").takeIf { !it.isNaN() }
                ?: obj.optDouble("price").takeIf { !it.isNaN() }
                ?: return null
            val change = obj.optDouble("Change", 0.0)
            val changePct = obj.optDouble("ChangePercent", 0.0)
            StockPrice(symbol.uppercase(), price, change, changePct)
        } catch (e: Exception) { null }
    }

    private fun parseHtmlPrice(symbol: String, html: String): StockPrice? {
        return try {
            val pricePatterns = listOf(
                Pattern.compile("last[_-]?price[\"']?\\s*[=:]\\s*[\"']?([0-9]+\\.?[0-9]*)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\"LastPrice\"\\s*:\\s*([0-9]+\\.?[0-9]*)"),
                Pattern.compile("<span[^>]*class=[\"'][^\"']*last[^\"']*[\"'][^>]*>([0-9]+\\.?[0-9]*)</span>"),
                Pattern.compile("Current Price[^0-9]*([0-9]+\\.?[0-9]*)"),
                Pattern.compile("آخر سعر[^0-9]*([0-9]+\\.?[0-9]*)")
            )
            for (pat in pricePatterns) {
                val m = pat.matcher(html)
                if (m.find()) {
                    val price = m.group(1)?.toDoubleOrNull() ?: continue
                    if (price > 0) return StockPrice(symbol.uppercase(), price, 0.0, 0.0)
                }
            }
            null
        } catch (e: Exception) { null }
    }

    fun clearCache() = cache.clear()
}
