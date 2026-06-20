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
    private val holdingDao = db.holdingDao()
    private val ipoAppDao = db.ipoApplicationDao()

    fun txnsInPeriod(start: Long, end: Long): Flow<List<TxnEntity>> = txnDao.inPeriod(start, end)

    fun txnsForCategory(start: Long, end: Long, category: String): Flow<List<TxnEntity>> =
        txnDao.inPeriodForCategory(start, end, category)

    fun txnsForMerchant(merchant: String): Flow<List<TxnEntity>> = txnDao.forMerchant(merchant)

    // ---- portfolio / investments ----
    fun holdings(): Flow<List<Holding>> = holdingDao.all()
    fun dividendsByCompany(): Flow<List<MerchantSum>> = txnDao.dividendsByCompany()
    fun ipoTxns(): Flow<List<TxnEntity>> = txnDao.ipoTxns()
    fun ipoApplications(): Flow<List<IpoApplication>> = ipoAppDao.all()
    fun incomeTxns(): Flow<List<TxnEntity>> = txnDao.incomeTxns()

    /**
     * Reads the SMS inbox, cleans + categorizes each transaction (applying learned
     * per-merchant rules), inserts new rows, and records AGM dates + IPO applications.
     * Idempotent. Returns rows imported.
     */
    suspend fun importFromSms(): Int = withContext(Dispatchers.IO) {
        val scan: SmsScan = SmsReader.read(appContext)
        val rules = ruleDao.all().associate { it.merchantClean to it.category }
        val before = txnDao.count()

        val entities = scan.txns.map { p: ParsedTxn ->
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

        // AGM invites: discover holdings (don't touch shares/price the user set).
        for (agm in scan.agms) {
            val name = agm.company.trim()
            if (name.isEmpty()) continue
            val existing = holdingDao.findByName(name)
            if (existing == null) {
                holdingDao.insert(Holding(name = name, nextAgmDate = agm.meetingDate))
            } else if (agm.meetingDate > existing.nextAgmDate) {
                holdingDao.setAgm(existing.id, agm.meetingDate)
            }
        }

        // IPO subscription requests.
        if (scan.ipoApps.isNotEmpty()) {
            ipoAppDao.insertAll(scan.ipoApps.map { IpoApplication(it.reference, it.date) })
        }

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

    // ---- holdings CRUD ----
    suspend fun addHolding(name: String, symbol: String, shares: Double, manualPrice: Double) =
        withContext(Dispatchers.IO) {
            holdingDao.insert(
                Holding(name = name.trim(), symbol = symbol.trim().uppercase(), shares = shares, manualPrice = manualPrice)
            )
        }

    suspend fun updateHolding(holding: Holding) = withContext(Dispatchers.IO) {
        holdingDao.update(holding.copy(symbol = holding.symbol.trim().uppercase(), name = holding.name.trim()))
    }

    suspend fun deleteHolding(holding: Holding) = withContext(Dispatchers.IO) {
        holdingDao.delete(holding)
    }

    /** Opt-in: fetch live prices for holdings that have a symbol. Returns count updated. */
    suspend fun refreshPrices(): Int = withContext(Dispatchers.IO) {
        if (!Prefs.getLiveMsxPrices(appContext)) return@withContext 0
        var updated = 0
        for (h in holdingDao.withSymbols()) {
            val price = MsxPriceSource.fetch(h.symbol)
            if (price != null && price > 0.0) {
                holdingDao.setPrice(h.id, price, System.currentTimeMillis())
                updated++
            }
        }
        updated
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) { txnDao.count() }
}
