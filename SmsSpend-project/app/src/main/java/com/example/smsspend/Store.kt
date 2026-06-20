package com.example.smsspend

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StockHolding(
    val symbol: String,
    val name: String,
    val shares: Double,
    val avgPrice: Double
)

object Store {
    private const val P = "smsspend"

    fun overrides(c: Context): MutableMap<String, String> {
        val s = c.getSharedPreferences(P, 0).getString("ov", "{}")!!
        val o = JSONObject(s)
        val m = HashMap<String, String>()
        for (k in o.keys()) m[k] = o.getString(k)
        return m
    }

    fun setOverride(c: Context, key: String, cat: String) {
        val m = overrides(c); m[key] = cat
        val o = JSONObject(); for ((k, v) in m) o.put(k, v)
        c.getSharedPreferences(P, 0).edit().putString("ov", o.toString()).apply()
    }

    fun saveSummary(c: Context, text: String, total: String) {
        c.getSharedPreferences(P, 0).edit()
            .putString("wtext", text).putString("wtotal", total).apply()
    }

    fun summary(c: Context): Pair<String, String> {
        val sp = c.getSharedPreferences(P, 0)
        return Pair(sp.getString("wtotal", "—")!!, sp.getString("wtext", "Open app to load")!!)
    }

    fun savePortfolio(c: Context, holdings: List<StockHolding>) {
        val arr = JSONArray()
        for (h in holdings) {
            arr.put(JSONObject().apply {
                put("symbol", h.symbol); put("name", h.name)
                put("shares", h.shares); put("avgPrice", h.avgPrice)
            })
        }
        c.getSharedPreferences(P, 0).edit().putString("portfolio", arr.toString()).apply()
    }

    fun loadPortfolio(c: Context): MutableList<StockHolding> {
        val json = c.getSharedPreferences(P, 0).getString("portfolio", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<StockHolding>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list += StockHolding(
                symbol = o.getString("symbol"),
                name = o.getString("name"),
                shares = o.getDouble("shares"),
                avgPrice = o.getDouble("avgPrice")
            )
        }
        return list
    }
}
