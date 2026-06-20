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
