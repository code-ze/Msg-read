package com.example.smsspend.data

import androidx.room.ColumnInfo
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
    // Added in schema v4; default keeps the migration ADD COLUMN in sync with Room's schema.
    @ColumnInfo(defaultValue = "''") val subcategory: String = "",
    val body: String
)

/**
 * A learned per-merchant categorization (category + optional sub-category). When the user
 * re-categorizes a merchant we store it here and bulk-update every matching [TxnEntity] —
 * fixing or sub-tagging one TALABAT fixes all, retroactively.
 */
@Entity(tableName = "merchant_rule")
data class MerchantRuleEntity(
    @PrimaryKey val merchantClean: String,
    val category: String,
    @ColumnInfo(defaultValue = "''") val subcategory: String = ""
)

/**
 * A category the user can see/manage. Built-in ones are seeded on first run; users can add
 * their own. A [parent] of "" means a top-level category (e.g. "Rent", "Utilities"); a set
 * [parent] makes this a sub-category under it (e.g. "Electricity" under "Utilities").
 */
@Entity(tableName = "category_def")
data class CategoryDef(
    @PrimaryKey val name: String,
    val parent: String = "",
    val color: Long = 0L,
    val sort: Int = 0,
    val builtIn: Boolean = false
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

/**
 * A running-balance reading scraped from a bank SMS ("رصيدك الحالي هو …"). Keyed by the SMS
 * timestamp so re-importing is idempotent. This builds a real balance-over-time series the
 * app uses for the current balance and for spend/runway predictions — no manual entry needed.
 */
@Entity(tableName = "balance_snapshot")
data class BalanceSnapshot(
    @PrimaryKey val date: Long,
    val balance: Double
)
