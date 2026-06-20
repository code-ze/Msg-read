package com.example.smsspend.data

import android.content.Context
import com.example.smsspend.parser.Categorizer
import com.example.smsspend.parser.ParsedTxn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class Repository(private val appContext: Context) {

    private val db = AppDatabase.get(appContext)
    private val txnDao = db.txnDao()
    private val ruleDao = db.merchantRuleDao()

    fun txnsInPeriod(start: Long, end: Long): Flow<List<TxnEntity>> = txnDao.inPeriod(start, end)

    fun txnsForCategory(start: Long, end: Long, category: String): Flow<List<TxnEntity>> =
        txnDao.inPeriodForCategory(start, end, category)

    fun txnsForMerchant(merchant: String): Flow<List<TxnEntity>> = txnDao.forMerchant(merchant)

    /**
     * Reads the SMS inbox, cleans + categorizes each transaction (applying learned
     * per-merchant rules), and inserts any new rows. Idempotent. Returns rows imported.
     */
    suspend fun importFromSms(): Int = withContext(Dispatchers.IO) {
        val parsed: List<ParsedTxn> = SmsReader.read(appContext)
        val rules = ruleDao.all().associate { it.merchantClean to it.category }
        val before = txnDao.count()
        val entities = parsed.map { p ->
            val clean = Categorizer.cleanMerchant(p.merchantRaw)
            val category = rules[clean] ?: Categorizer.defaultCategory(p.type, clean)
            TxnEntity(
                key = p.key,
                type = p.type.name,
                amount = p.amount,
                merchantRaw = p.merchantRaw,
                merchantClean = clean,
                date = p.date,
                category = category,
                body = p.body
            )
        }
        txnDao.insertAll(entities)
        txnDao.count() - before
    }

    /**
     * Learns a category for a merchant and retroactively reassigns every transaction from
     * that merchant — the "automatically re-categorize" behavior.
     */
    suspend fun setMerchantCategory(merchant: String, category: String) = withContext(Dispatchers.IO) {
        ruleDao.upsert(MerchantRuleEntity(merchant, category))
        txnDao.reassignMerchant(merchant, category)
    }

    suspend fun ruleFor(merchant: String): String? = withContext(Dispatchers.IO) {
        ruleDao.categoryFor(merchant)
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) { txnDao.count() }
}
