package com.example.smsspend.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Aggregated spend for one category within a period. */
data class CategorySum(val category: String, val total: Double, val count: Int)

/** Aggregated spend for one merchant within a period. */
data class MerchantSum(val merchantClean: String, val total: Double, val count: Int)

@Dao
interface TxnDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TxnEntity>): List<Long>

    @Query("SELECT * FROM txn WHERE date >= :start AND date < :end ORDER BY date DESC")
    fun inPeriod(start: Long, end: Long): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txn WHERE date >= :start AND date < :end AND category = :category ORDER BY date DESC")
    fun inPeriodForCategory(start: Long, end: Long, category: String): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txn WHERE merchantClean = :merchant ORDER BY date DESC")
    fun forMerchant(merchant: String): Flow<List<TxnEntity>>

    @Query("UPDATE txn SET category = :category WHERE merchantClean = :merchant")
    suspend fun reassignMerchant(merchant: String, category: String)

    @Query("SELECT COUNT(*) FROM txn")
    suspend fun count(): Int

    @Query("DELETE FROM txn")
    suspend fun clear()
}

@Dao
interface MerchantRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("SELECT * FROM merchant_rule")
    suspend fun all(): List<MerchantRuleEntity>

    @Query("SELECT category FROM merchant_rule WHERE merchantClean = :merchant LIMIT 1")
    suspend fun categoryFor(merchant: String): String?
}
