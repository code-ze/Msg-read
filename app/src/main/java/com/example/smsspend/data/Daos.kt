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

/** Aggregated spend for one sub-category within a period. */
data class SubcategorySum(val subcategory: String, val total: Double, val count: Int)

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

    @Query("UPDATE txn SET category = :category, subcategory = :subcategory WHERE merchantClean = :merchant")
    suspend fun reassignMerchant(merchant: String, category: String, subcategory: String)

    /** Sub-category split within one category for a period (e.g. Utilities → Electricity/Water). */
    @Query(
        "SELECT subcategory, SUM(amount) AS total, COUNT(*) AS count FROM txn " +
            "WHERE date >= :start AND date < :end AND category = :category " +
            "GROUP BY subcategory ORDER BY total DESC"
    )
    fun subcategoriesInPeriod(start: Long, end: Long, category: String): Flow<List<SubcategorySum>>

    @Query("SELECT merchantClean, SUM(amount) AS total, COUNT(*) AS count FROM txn WHERE type = 'DIVIDEND' GROUP BY merchantClean ORDER BY total DESC")
    fun dividendsByCompany(): Flow<List<MerchantSum>>

    @Query("SELECT * FROM txn WHERE type = 'IPO' ORDER BY date DESC")
    fun ipoTxns(): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txn WHERE type IN ('DEPOSIT', 'WALLET_IN') ORDER BY date")
    fun incomeTxns(): Flow<List<TxnEntity>>

    /** Bank deposits only (salary lands as a deposit, not a wallet transfer). */
    @Query("SELECT * FROM txn WHERE type = 'DEPOSIT' ORDER BY date")
    fun depositTxns(): Flow<List<TxnEntity>>

    /** Per-merchant spend within a period (granular "where the money goes"). */
    @Query(
        "SELECT merchantClean, SUM(amount) AS total, COUNT(*) AS count FROM txn " +
            "WHERE date >= :start AND date < :end AND type IN ('DEBIT', 'WALLET_OUT') " +
            "AND category NOT IN ('Income', 'Dividends') " +
            "GROUP BY merchantClean ORDER BY total DESC"
    )
    fun merchantsInPeriod(start: Long, end: Long): Flow<List<MerchantSum>>

    /** Spend per category since [start] (for rolling baselines used in fixed-cost audits). */
    @Query(
        "SELECT category, SUM(amount) AS total, COUNT(*) AS count FROM txn " +
            "WHERE date >= :start AND type IN ('DEBIT', 'WALLET_OUT') " +
            "AND category NOT IN ('Income', 'Dividends') " +
            "GROUP BY category"
    )
    fun categorySpendSince(start: Long): Flow<List<CategorySum>>

    @Query("SELECT * FROM txn ORDER BY date DESC")
    suspend fun allForExport(): List<TxnEntity>

    @Query("SELECT COUNT(*) FROM txn")
    suspend fun count(): Int

    @Query("DELETE FROM txn")
    suspend fun clear()
}

@Dao
interface BalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BalanceSnapshot>)

    /** Most recent balance reading (the closest thing to "money in the bank right now"). */
    @Query("SELECT * FROM balance_snapshot ORDER BY date DESC LIMIT 1")
    fun latest(): Flow<BalanceSnapshot?>

    /** Whole balance series, oldest first, for the trend sparkline. */
    @Query("SELECT * FROM balance_snapshot ORDER BY date ASC")
    fun series(): Flow<List<BalanceSnapshot>>

    @Query("SELECT * FROM balance_snapshot ORDER BY date DESC")
    suspend fun allForExport(): List<BalanceSnapshot>
}

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holding ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Holding>>

    @Query("SELECT * FROM holding WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Holding?

    @Query("SELECT * FROM holding WHERE symbol != ''")
    suspend fun withSymbols(): List<Holding>

    @Query("SELECT * FROM holding ORDER BY name COLLATE NOCASE")
    suspend fun allForExport(): List<Holding>

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

    @Query("SELECT * FROM ipo_application ORDER BY date DESC")
    suspend fun allForExport(): List<IpoApplication>
}

@Dao
interface MerchantRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("SELECT * FROM merchant_rule")
    suspend fun all(): List<MerchantRuleEntity>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CategoryDef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CategoryDef)

    @Query("SELECT * FROM category_def ORDER BY sort, name COLLATE NOCASE")
    fun all(): Flow<List<CategoryDef>>

    @Query("SELECT * FROM category_def ORDER BY sort, name COLLATE NOCASE")
    suspend fun allOnce(): List<CategoryDef>

    @Query("SELECT COUNT(*) FROM category_def")
    suspend fun count(): Int

    @Query("DELETE FROM category_def WHERE name = :name AND builtIn = 0")
    suspend fun deleteCustom(name: String)
}
