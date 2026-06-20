package com.example.smsspend.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Query("SELECT merchantClean, SUM(amount) AS total, COUNT(*) AS count FROM txn WHERE type = 'DIVIDEND' GROUP BY merchantClean ORDER BY total DESC")
    fun dividendsByCompany(): Flow<List<MerchantSum>>

    @Query("SELECT * FROM txn WHERE type = 'IPO' ORDER BY date DESC")
    fun ipoTxns(): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txn WHERE type IN ('DEPOSIT', 'WALLET_IN') ORDER BY date")
    fun incomeTxns(): Flow<List<TxnEntity>>

    @Query("SELECT COUNT(*) FROM txn")
    suspend fun count(): Int

    @Query("DELETE FROM txn")
    suspend fun clear()
}

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holding ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Holding>>

    @Query("SELECT * FROM holding WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Holding?

    @Query("SELECT * FROM holding WHERE symbol != ''")
    suspend fun withSymbols(): List<Holding>

    @Insert
    suspend fun insert(holding: Holding): Long

    @Update
    suspend fun update(holding: Holding)

    @Delete
    suspend fun delete(holding: Holding)

    @Query("UPDATE holding SET lastPrice = :price, lastPriceAt = :time WHERE id = :id")
    suspend fun setPrice(id: Long, price: Double, time: Long)

    @Query("UPDATE holding SET nextAgmDate = :agm WHERE id = :id")
    suspend fun setAgm(id: Long, agm: Long)
}

@Dao
interface IpoApplicationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<IpoApplication>)

    @Query("SELECT * FROM ipo_application ORDER BY date DESC")
    fun all(): Flow<List<IpoApplication>>
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
