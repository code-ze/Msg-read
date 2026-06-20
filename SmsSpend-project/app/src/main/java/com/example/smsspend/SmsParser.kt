package com.example.smsspend

import android.content.Context
import android.provider.Telephony
import kotlin.math.abs

data class Txn(
    val type: String,
    val amount: Double,
    val merchant: String,
    val date: Long,
    val key: String,
    var category: String
)

object SmsParser {
    private val beforeOmr = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*OMR")
    private val afterOmr = Regex("OMR\\s+([0-9]+(?:[.,][0-9]+)?)")
    private val debitMerch = Regex("المباشر في (.+?) بتاريخ")
    private val sentTo = Regex("إلى (.+?) من حسابك")
    private val recvFrom = Regex("من (.+?) في حسابك")
    private val code6 = Regex("^[0-9]{6}-")

    fun load(ctx: Context): List<Txn> {
        val out = ArrayList<Txn>()
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val cols = arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cur = ctx.contentResolver.query(uri, cols, null, null, Telephony.Sms.DATE + " DESC")
            ?: return out
        cur.use {
            val bi = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val di = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                val body = it.getString(bi) ?: continue
                if (!body.contains("OMR")) continue
                val date = it.getLong(di)
                val t = parse(body, date) ?: continue
                out.add(t)
            }
        }
        return out
    }

    private fun num(s: String) = s.replace(",", "").toDoubleOrNull() ?: 0.0

    fun parse(body: String, date: Long): Txn? {
        val key = abs(body.hashCode()).toString()
        return when {
            body.contains("بطاقة الخصم المباشر") -> {
                val amt = beforeOmr.find(body)?.groupValues?.get(1)?.let { num(it) } ?: return null
                var m = debitMerch.find(body)?.groupValues?.get(1)?.trim() ?: "?"
                m = code6.replace(m, "")
                Txn("debit", amt, m, date, key, categorize(m))
            }
            body.contains("لقد قمت بإرسال") -> {
                val amt = afterOmr.find(body)?.groupValues?.get(1)?.let { num(it) } ?: return null
                val m = sentTo.find(body)?.groupValues?.get(1)?.trim() ?: "?"
                Txn("walletOut", amt, m, date, key, "Transfers")
            }
            body.contains("لقد استلمت") -> {
                val amt = afterOmr.find(body)?.groupValues?.get(1)?.let { num(it) } ?: return null
                val m = recvFrom.find(body)?.groupValues?.get(1)?.trim() ?: "?"
                Txn("walletIn", amt, m, date, key, "Income")
            }
            body.contains("تم إيداع") -> {
                val amt = beforeOmr.find(body)?.groupValues?.get(1)?.let { num(it) } ?: return null
                Txn("deposit", amt, "Deposit", date, key, "Income")
            }
            else -> null
        }
    }

    private val rules = listOf(
        "Food Delivery" to listOf("TALABAT"),
        "Cafes & Tea" to listOf("TEA TIME", "AROMA", "FUN JUICE", "KYAN BARK", "EFENDI"),
        "Restaurants" to listOf("RESTURANT", "RESTAURANT", "TEXAS CHICKEN", "SHAWARMA", "HEQBAH", "CHOCOLALA", "KUCU"),
        "Groceries" to listOf("LULU", "RAHAL", "FRESH EASY", "ABU TURKEY", "BASAMAT", "EXPRESS SHOPPING", "ATLAS", "RABI", "GHORBAN", "BRIGHT GULF", "AHLAIN", "MARKET"),
        "Fuel & Transport" to listOf("OMAN OIL", "SHELL", "PRECISION TUNE", "AL MAHA", "1718"),
        "Pharmacy & Health" to listOf("PHARMACY", "HOSPITAL", "CLINIC"),
        "Telecom" to listOf("OMANTEL", "OOREDOO"),
        "Utilities" to listOf("NAMA"),
        "Subscriptions" to listOf("ANTHROPIC", "CLAUDE", "NETFLIX", "SPOTIFY"),
        "Online Shopping" to listOf("ALIEXPRESS", "AMAZON", "NOON"),
        "Charity" to listOf("SADAQAT", "DAR AL ATTA", "M.O.A")
    )

    fun categorize(merchant: String): String {
        val m = merchant.uppercase()
        for ((cat, keys) in rules) if (keys.any { m.contains(it) }) return cat
        return "Other"
    }
}
