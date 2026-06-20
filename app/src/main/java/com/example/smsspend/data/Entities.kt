package com.example.smsspend.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One imported transaction. [key] is a stable hash of the SMS body+date so re-importing
 * the inbox is idempotent. [category] is materialized (not derived at read time) so the
 * dashboard can group/sum in SQL; it is recomputed on import and bulk-updated whenever a
 * learned rule changes.
 */
@Entity(tableName = "txn")
data class TxnEntity(
    @PrimaryKey val key: String,
    val type: String,
    val amount: Double,
    val merchantRaw: String,
    val merchantClean: String,
    val date: Long,
    val category: String,
    val body: String
)

/**
 * A learned per-merchant categorization. When the user re-categorizes a merchant we store
 * it here and bulk-update every matching [TxnEntity] — fixing one TALABAT fixes all.
 */
@Entity(tableName = "merchant_rule")
data class MerchantRuleEntity(
    @PrimaryKey val merchantClean: String,
    val category: String
)

/**
 * A stock holding the user tracks. [shares] and [manualPrice] are user-entered;
 * [lastPrice]/[lastPriceAt] are filled by the opt-in MSX fetch. [nextAgmDate] is
 * auto-discovered from AGM invite SMS. Market value = shares × effective price.
 */
@Entity(tableName = "holding")
data class Holding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val symbol: String = "",
    val shares: Double = 0.0,
    val manualPrice: Double = 0.0,
    val lastPrice: Double = 0.0,
    val lastPriceAt: Long = 0L,
    val nextAgmDate: Long = 0L
) {
    /** Price to value the holding with: a fresh fetched price if present, else the manual one. */
    fun effectivePrice(): Double = if (lastPrice > 0.0) lastPrice else manualPrice
    fun marketValue(): Double = shares * effectivePrice()
}

/** An IPO subscription-request confirmation (MCD), identified by its reference number. */
@Entity(tableName = "ipo_application")
data class IpoApplication(
    @PrimaryKey val reference: String,
    val date: Long
)
