package com.example.smsspend.data

import android.content.Context
import com.example.smsspend.parser.Categorizer
import com.example.smsspend.parser.CompanyNames
import com.example.smsspend.parser.ParsedTxn
import com.example.smsspend.parser.TxnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class Repository(private val appContext: Context) {

    private val db = AppDatabase.get(appContext)
    private val txnDao = db.txnDao()
    private val ruleDao = db.merchantRuleDao()
    private val holdingDao = db.holdingDao()
    private val ipoAppDao = db.ipoApplicationDao()
    private val balanceDao = db.balanceDao()
    private val categoryDao = db.categoryDao()
    private val cardLimitDao = db.cardLimitDao()

    fun txnsInPeriod(start: Long, end: Long): Flow<List<TxnEntity>> = txnDao.inPeriod(start, end)

    fun txnsForCategory(start: Long, end: Long, category: String): Flow<List<TxnEntity>> =
        txnDao.inPeriodForCategory(start, end, category)

    fun txnsForMerchant(merchant: String): Flow<List<TxnEntity>> = txnDao.forMerchant(merchant)

    suspend fun txnsBetween(start: Long, end: Long): List<TxnEntity> =
        withContext(Dispatchers.IO) { txnDao.between(start, end) }

    fun merchantsInPeriod(start: Long, end: Long): Flow<List<MerchantSum>> =
        txnDao.merchantsInPeriod(start, end)

    fun subcategoriesInPeriod(start: Long, end: Long, category: String): Flow<List<SubcategorySum>> =
        txnDao.subcategoriesInPeriod(start, end, category)

    fun categorySpendSince(start: Long): Flow<List<CategorySum>> =
        txnDao.categorySpendSince(start)

    // ---- categories ----
    fun categories(): Flow<List<CategoryDef>> = categoryDao.all()

    /** Seeds the built-in categories + sub-categories once, so users have sensible defaults. */
    suspend fun seedCategoriesIfEmpty() = withContext(Dispatchers.IO) {
        if (categoryDao.count() > 0) return@withContext
        val defs = ArrayList<CategoryDef>()
        Categorizer.allCategories.forEachIndexed { i, name ->
            defs.add(CategoryDef(name = name, parent = "", sort = i, builtIn = true))
        }
        Categorizer.builtInSubcategories.forEach { (parent, subs) ->
            subs.forEachIndexed { i, sub ->
                defs.add(CategoryDef(name = sub, parent = parent, sort = i, builtIn = true))
            }
        }
        categoryDao.insertAll(defs)
    }

    suspend fun addCategory(name: String, parent: String) = withContext(Dispatchers.IO) {
        val n = name.trim()
        if (n.isNotEmpty()) categoryDao.upsert(CategoryDef(name = n, parent = parent.trim(), sort = 999))
    }

    suspend fun deleteCategory(name: String) = withContext(Dispatchers.IO) {
        categoryDao.deleteCustom(name)
    }

    // ---- balance ----
    fun latestBalance(): Flow<BalanceSnapshot?> = balanceDao.latest()
    fun balanceSeries(): Flow<List<BalanceSnapshot>> = balanceDao.series()

    // ---- credit cards ----
    fun cardLimits(): Flow<List<CardLimitSnapshot>> = cardLimitDao.latestPerCard()
    fun cardPeaks(): Flow<List<CardPeak>> = cardLimitDao.peakPerCard()

    // ---- portfolio / investments ----
    fun holdings(): Flow<List<Holding>> = holdingDao.all()
    fun dividendsByCompany(): Flow<List<MerchantSum>> = txnDao.dividendsByCompany()
    fun ipoTxns(): Flow<List<TxnEntity>> = txnDao.ipoTxns()
    fun ipoApplications(): Flow<List<IpoApplication>> = ipoAppDao.all()
    fun incomeTxns(): Flow<List<TxnEntity>> = txnDao.incomeTxns()
    fun depositTxns(): Flow<List<TxnEntity>> = txnDao.depositTxns()

    /**
     * Reads the SMS inbox, cleans + categorizes each transaction (applying learned
     * per-merchant rules), inserts new rows, and records AGM dates + IPO applications.
     * Idempotent. Returns rows imported.
     */
    suspend fun importFromSms(): Int = withContext(Dispatchers.IO) {
        val scan: SmsScan = SmsReader.read(appContext)
        seedCategoriesIfEmpty()
        val rules = ruleDao.all().associateBy { it.merchantClean }
        val before = txnDao.count()

        val entities = scan.txns.map { p: ParsedTxn ->
            var clean = Categorizer.cleanMerchant(p.merchantRaw)
            // Unify company name variants (English/Arabic/codes) for investments + dividends,
            // so the same company isn't split across rows.
            if (p.type == TxnType.DIVIDEND || p.type == TxnType.IPO) {
                clean = CompanyNames.canonical(clean)
            }
            val rule = rules[clean]
            val category = rule?.category ?: Categorizer.defaultCategory(p.type, clean)
            val subcategory = rule?.subcategory ?: Categorizer.defaultSubcategory(category, clean)
            TxnEntity(
                key = p.key,
                type = p.type.name,
                amount = p.amount,
                merchantRaw = p.merchantRaw,
                merchantClean = clean,
                date = p.date,
                category = category,
                subcategory = subcategory,
                body = p.body,
                currency = p.currency,
                originalAmount = p.originalAmount,
                amountExact = p.amountExact
            )
        }
        txnDao.insertAll(entities)

        // AGM invites: discover holdings (don't touch shares/price the user set).
        for (agm in scan.agms) {
            val name = CompanyNames.canonical(agm.company.trim())
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

        // Running balance readings -> balance-over-time series for trends/predictions.
        if (scan.balances.isNotEmpty()) {
            balanceDao.insertAll(scan.balances.map { BalanceSnapshot(it.date, it.balance) })
        }

        // Remaining credit per card -> "owed on card" without the user typing anything.
        if (scan.cardLimits.isNotEmpty()) {
            cardLimitDao.insertAll(
                scan.cardLimits.map { CardLimitSnapshot(it.date, it.cardLast4, it.availableLimit) }
            )
        }

        txnDao.count() - before
    }

    // ---- manual entries & edits ----

    /**
     * Records a transaction the bank never messaged about — a cash spend, or a refund that only
     * shows up in the card statement. A refund is stored as a negative [amount] so it nets out of
     * every total, category and merchant figure without any special-casing downstream.
     */
    suspend fun addManualTxn(
        amount: Double,
        merchant: String,
        category: String,
        subcategory: String,
        date: Long,
        type: TxnType
    ): Unit = withContext(Dispatchers.IO) {
        val clean = Categorizer.cleanMerchant(merchant).ifBlank { merchant.trim() }
        txnDao.upsert(
            TxnEntity(
                key = "manual-" + java.util.UUID.randomUUID(),
                type = type.name,
                amount = amount,
                merchantRaw = merchant.trim(),
                merchantClean = clean,
                date = date,
                category = category,
                subcategory = subcategory,
                body = "",
                manual = true
            )
        )
    }

    /**
     * Saves an edit. Works for imported rows too: inserts use IGNORE, so a re-import will not
     * overwrite what the user corrected here.
     */
    suspend fun updateTxn(txn: TxnEntity): Unit = withContext(Dispatchers.IO) {
        txnDao.upsert(txn)
    }

    suspend fun hideTxn(key: String): Unit = withContext(Dispatchers.IO) { txnDao.hide(key) }
    suspend fun unhideTxn(key: String): Unit = withContext(Dispatchers.IO) { txnDao.unhide(key) }

    /**
     * Learns a category + sub-category for a merchant and retroactively reassigns every
     * transaction from that merchant — so tagging one TALABAT (or splitting a NAMA bill into
     * "Electricity") applies to all its past spends automatically.
     */
    suspend fun setMerchantCategory(
        merchant: String,
        category: String,
        subcategory: String = ""
    ) = withContext(Dispatchers.IO) {
        ruleDao.upsert(MerchantRuleEntity(merchant, category, subcategory))
        txnDao.reassignMerchant(merchant, category, subcategory)
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

    /**
     * Refreshes the mid-market rate table used for the bank-markup figure.
     *
     * Only goes to the network when the cache is past its TTL, and keeps whatever is already
     * cached if the fetch fails — a rate a few hours (or days) old still prices a markup far
     * better than nothing, since these are reference rates that barely move.
     */
    suspend fun refreshFxRates(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!Prefs.getLiveFxRates(appContext)) return@withContext false
        if (!force && !Prefs.fxRatesStale(appContext)) return@withContext false
        val snap = FxRateSource.fetch() ?: return@withContext false
        Prefs.setFxRates(appContext, snap.perUsd, snap.fetchedAt)
        true
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

    /**
     * Builds a complete JSON snapshot of everything the app holds — transactions, learned
     * rules, holdings, IPO applications and the balance series, plus the user's settings.
     * Used by the "export / copy data" debug feature so it can be fed back for analysis.
     * On-device only; the user chooses where it goes.
     */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "SMS Spend")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("schemaVersion", 5)

        root.put("settings", JSONObject().apply {
            put("anchorDay", Prefs.getAnchorDay(appContext))
            put("salaryAmount", Prefs.getSalaryAmount(appContext))
            put("manualBalance", Prefs.getManualBalance(appContext))
            put("investAsSpending", Prefs.getInvestAsSpending(appContext))
            put("liveMsxPrices", Prefs.getLiveMsxPrices(appContext))
            put("creditLimit", Prefs.getCreditLimit(appContext))
            put("dailyLimit", Prefs.getDailyLimit(appContext))
            put("monthlyBudget", Prefs.getMonthlyBudget(appContext))
        })

        val txns = txnDao.allForExport()
        root.put("transactionCount", txns.size)
        root.put("transactions", JSONArray().apply {
            for (t in txns) put(JSONObject().apply {
                put("date", t.date)
                put("type", t.type)
                put("amount", t.amount)
                put("merchant", t.merchantClean)
                put("category", t.category)
                put("subcategory", t.subcategory)
                if (t.isForeign) {
                    put("currency", t.currency)
                    put("originalAmount", t.originalAmount)
                }
            })
        })

        root.put("cardLimits", JSONArray().apply {
            for (c in cardLimitDao.allForExport()) put(JSONObject().apply {
                put("date", c.date)
                put("cardLast4", c.cardLast4)
                put("availableLimit", c.availableLimit)
            })
        })

        root.put("rules", JSONArray().apply {
            for (r in ruleDao.all()) put(JSONObject().apply {
                put("merchant", r.merchantClean)
                put("category", r.category)
                put("subcategory", r.subcategory)
            })
        })

        root.put("categories", JSONArray().apply {
            for (c in categoryDao.allOnce()) put(JSONObject().apply {
                put("name", c.name)
                put("parent", c.parent)
                put("builtIn", c.builtIn)
            })
        })

        root.put("holdings", JSONArray().apply {
            for (h in holdingDao.allForExport()) put(JSONObject().apply {
                put("name", h.name)
                put("symbol", h.symbol)
                put("shares", h.shares)
                put("manualPrice", h.manualPrice)
                put("lastPrice", h.lastPrice)
                put("nextAgmDate", h.nextAgmDate)
            })
        })

        root.put("ipoApplications", JSONArray().apply {
            for (a in ipoAppDao.allForExport()) put(JSONObject().apply {
                put("reference", a.reference)
                put("date", a.date)
            })
        })

        root.put("balances", JSONArray().apply {
            for (b in balanceDao.allForExport()) put(JSONObject().apply {
                put("date", b.date)
                put("balance", b.balance)
            })
        })

        root.toString(2)
    }
}
