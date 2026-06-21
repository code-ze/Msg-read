package com.example.smsspend.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsspend.data.BalanceSnapshot
import com.example.smsspend.data.CategoryDef
import com.example.smsspend.data.Holding
import com.example.smsspend.data.IpoApplication
import com.example.smsspend.data.MerchantSum
import com.example.smsspend.data.Prefs
import com.example.smsspend.data.Repository
import com.example.smsspend.data.TxnEntity
import com.example.smsspend.model.Insights
import com.example.smsspend.model.Period
import com.example.smsspend.model.Periods
import com.example.smsspend.model.Recommendations
import com.example.smsspend.model.SalaryDetector
import com.example.smsspend.model.Totals
import com.example.smsspend.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A request for a period; resolved to a concrete [Period] using the current anchor day. */
sealed interface PeriodReq {
    /** Pay cycle bounded by auto-detected salary deposits (falls back to fixed anchor). */
    data class Salary(val offset: Int) : PeriodReq
    data class Cycle(val offset: Int) : PeriodReq
    data class Month(val offset: Int) : PeriodReq
    data class Year(val offset: Int) : PeriodReq
    data class Custom(val startDay: Long, val endDay: Long) : PeriodReq
}

sealed interface Screen {
    data object Dashboard : Screen
    data object Transactions : Screen
    data object Analytics : Screen
    data object Insights : Screen
    data object Investments : Screen
    data object Retire : Screen
    data class Category(val name: String) : Screen
    data class Merchant(val name: String) : Screen
    data object Settings : Screen
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)

    val backStack = MutableStateFlow<List<Screen>>(listOf(Screen.Dashboard))
    val current: Screen get() = backStack.value.last()

    val anchorDay = MutableStateFlow(Prefs.getAnchorDay(app))
    val investAsSpending = MutableStateFlow(Prefs.getInvestAsSpending(app))
    val liveMsxPrices = MutableStateFlow(Prefs.getLiveMsxPrices(app))
    val salaryAmount = MutableStateFlow(Prefs.getSalaryAmount(app))
    val manualBalance = MutableStateFlow(Prefs.getManualBalance(app))

    val periodReq = MutableStateFlow<PeriodReq>(PeriodReq.Salary(0))

    val importStatus = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(false)

    init { viewModelScope.launch { repo.seedCategoriesIfEmpty() } }

    /** All category definitions (built-in + custom), top-level and sub. */
    val categories: StateFlow<List<CategoryDef>> =
        repo.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Balance readings over time (oldest first) — drives the trend chart + historical balance. */
    val balancePoints: StateFlow<List<BalanceSnapshot>> =
        repo.balanceSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Balance "right now": the user's manual figure if set, else the latest scraped reading. */
    val currentBalance: StateFlow<Double> =
        combine(balancePoints, manualBalance) { pts, manual ->
            if (manual > 0.0) manual else pts.lastOrNull()?.balance ?: 0.0
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Auto-detected salary deposit dates (pinned amount wins) used as pay-cycle boundaries. */
    val salaryDates: StateFlow<List<Long>> =
        combine(repo.depositTxns(), salaryAmount) { list, pinned ->
            SalaryDetector.detect(list.map { SalaryDetector.Income(it.amount, it.date) }, pinned)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val period: StateFlow<Period> =
        combine(periodReq, anchorDay, salaryDates) { req, anchor, sal -> resolve(req, anchor, sal) }
            .stateIn(
                viewModelScope, SharingStarted.Eagerly,
                resolve(periodReq.value, anchorDay.value, salaryDates.value)
            )

    /**
     * Balance to show for the *viewed* period: the latest reading at or before the period end
     * (so old periods show the total as it was back then). The manual override applies only to
     * the current period.
     */
    val balance: StateFlow<Double> =
        combine(balancePoints, manualBalance, period) { pts, manual, p ->
            val now = System.currentTimeMillis()
            if (p.endExclusive > now && manual > 0.0) return@combine manual
            val cutoff = minOf(p.endExclusive, now)
            pts.lastOrNull { it.date <= cutoff }?.balance
                ?: pts.lastOrNull()?.balance
                ?: manual
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Trend points for the chart, scoped to readings up to the viewed period's end. */
    val balanceSeries: StateFlow<List<BalanceSnapshot>> =
        combine(balancePoints, period) { pts, p ->
            val cutoff = minOf(p.endExclusive, System.currentTimeMillis())
            pts.filter { it.date <= cutoff }.ifEmpty { pts }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val periodTxns: StateFlow<List<TxnEntity>> =
        period.flatMapLatest { p -> repo.txnsInPeriod(p.start, p.endExclusive) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totals: StateFlow<Totals> =
        combine(periodTxns, investAsSpending) { txns, invest -> Totals.from(txns, invest) }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5000),
                Totals(0.0, 0.0, 0.0, 0.0, emptyList())
            )

    val insights: StateFlow<Insights> =
        combine(periodTxns, period, balance) { txns, p, bal ->
            Insights.compute(txns, p, System.currentTimeMillis(), bal)
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            Insights.compute(emptyList(), period.value, System.currentTimeMillis(), 0.0)
        )

    val topMerchants: StateFlow<List<MerchantSum>> =
        period.flatMapLatest { p -> repo.merchantsInPeriod(p.start, p.endExclusive) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Spending in the previous comparable period, for trend deltas (null req -> 0). */
    val prevSpent: StateFlow<Double> =
        combine(periodReq, anchorDay, salaryDates) { req, anchor, sal -> resolvePrev(req, anchor, sal) }
            .flatMapLatest { p ->
                if (p == null) flowOf(0.0)
                else repo.txnsInPeriod(p.start, p.endExclusive)
                    .map { list -> list.filter { Insights.isSpending(it) }.sumOf { it.amount } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val recentTxns: StateFlow<List<TxnEntity>> = periodTxns

    private val dayMs = 24L * 60 * 60 * 1000

    /**
     * Days from *today* until the next salary — based on the real current cycle, NOT the viewed
     * period (so browsing 2024 doesn't break "safe to spend"). Always 1..cycleLength.
     */
    private fun currentRemainingDays(sal: List<Long>, anchor: Int): Int {
        val now = System.currentTimeMillis()
        val cycle = Periods.salaryCycle(sal, 0, now) ?: Periods.payCycle(anchor, 0, now)
        return ((cycle.endExclusive - now) / dayMs).toInt().coerceIn(1, 45)
    }

    /** Daily allowance that keeps the (current) balance to the next salary (Safe-to-Spend). */
    val safeToSpend: StateFlow<Double> =
        combine(currentBalance, salaryDates, anchorDay) { bal, sal, anchor ->
            Recommendations.safeDailyAllowance(bal, currentRemainingDays(sal, anchor))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val baselineStart = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
    private val baselineSpend: StateFlow<List<com.example.smsspend.data.CategorySum>> =
        repo.categorySpendSince(baselineStart)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Rough monthly surplus you could invest: ~90-day income minus spend, per month. */
    val monthlyNetEstimate: StateFlow<Double> =
        combine(repo.incomeTxns(), baselineSpend) { inc, spend ->
            val income90 = inc.filter { it.date >= baselineStart }.sumOf { it.amount }
            val spend90 = spend.sumOf { it.total }
            ((income90 - spend90) / 3.0).coerceAtLeast(0.0)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Actionable savings/investing recommendations for the Insights & Action tab. */
    val recommendations: StateFlow<List<Recommendations.Rec>> =
        combine(totals, insights, currentBalance, baselineSpend, salaryDates) { t, ins, bal, baseline, sal ->
            buildRecommendations(t, ins, bal, baseline, currentRemainingDays(sal, anchorDay.value))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildRecommendations(
        t: Totals,
        ins: Insights,
        bal: Double,
        baseline: List<com.example.smsspend.data.CategorySum>,
        daysLeftInCycle: Int
    ): List<Recommendations.Rec> {
        val convenience = setOf("Food Delivery", "Cafes & Tea")
        val leaks = t.byCategory
            .filter { it.category in convenience }
            .map { Recommendations.Leak(it.category, it.count, it.total) }

        val fixedCats = setOf("Utilities", "Telecom", "Fuel & Transport")
        val baseByCat = baseline.associate { it.category to it.total }
        val periodDays = ins.daysTotal.coerceAtLeast(1)
        val fixed = fixedCats.mapNotNull { cat ->
            val current = t.byCategory.firstOrNull { it.category == cat }?.total ?: return@mapNotNull null
            if (current <= 0) return@mapNotNull null
            val ninetyDay = baseByCat[cat] ?: 0.0
            val baselineForPeriod = ninetyDay / 90.0 * periodDays
            Recommendations.FixedCost(cat, current, baselineForPeriod)
        }

        return Recommendations.analyze(
            balance = bal,
            perDay = ins.perDay,
            daysLeftInCycle = daysLeftInCycle,
            monthlyEssentialSpend = ins.perDay * 30,
            leaks = leaks,
            fixed = fixed
        )
    }

    fun txnsForCategory(start: Long, end: Long, category: String) =
        repo.txnsForCategory(start, end, category)

    fun txnsForMerchant(merchant: String) = repo.txnsForMerchant(merchant)

    /** Loads the biggest transactions in a ±3-day window around a tapped balance-graph point. */
    fun loadTxnsAround(date: Long, onResult: (List<TxnEntity>) -> Unit) {
        viewModelScope.launch { onResult(repo.txnsBetween(date - 3 * dayMs, date + 3 * dayMs)) }
    }

    fun subcategoriesInPeriod(start: Long, end: Long, category: String) =
        repo.subcategoriesInPeriod(start, end, category)

    /** Sub-categories defined under [parent], for the picker. */
    fun subcategoriesOf(parent: String): List<String> =
        categories.value.filter { it.parent == parent }.map { it.name }

    fun addCategory(name: String, parent: String) {
        viewModelScope.launch { repo.addCategory(name, parent) }
    }
    fun deleteCategory(name: String) {
        viewModelScope.launch { repo.deleteCategory(name) }
    }

    // ---- portfolio ----
    val holdings: StateFlow<List<Holding>> =
        repo.holdings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val dividendsByCompany: StateFlow<List<MerchantSum>> =
        repo.dividendsByCompany().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ipoTxns: StateFlow<List<TxnEntity>> =
        repo.ipoTxns().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ipoApplications: StateFlow<List<IpoApplication>> =
        repo.ipoApplications().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pricesLoading = MutableStateFlow(false)

    fun addHolding(name: String, symbol: String, shares: Double, manualPrice: Double) {
        viewModelScope.launch { repo.addHolding(name, symbol, shares, manualPrice) }
    }
    fun updateHolding(holding: Holding) { viewModelScope.launch { repo.updateHolding(holding) } }
    fun deleteHolding(holding: Holding) { viewModelScope.launch { repo.deleteHolding(holding) } }

    fun refreshPrices() {
        viewModelScope.launch {
            pricesLoading.value = true
            try {
                val n = repo.refreshPrices()
                importStatus.value = if (n > 0) "Updated $n price(s)" else "No prices fetched"
            } catch (e: Exception) {
                importStatus.value = "Price fetch failed"
            } finally {
                pricesLoading.value = false
            }
        }
    }

    fun setLiveMsxPrices(v: Boolean) {
        Prefs.setLiveMsxPrices(getApplication<Application>(), v)
        liveMsxPrices.value = v
    }

    private fun resolve(req: PeriodReq, anchor: Int, salaryDates: List<Long>): Period {
        val now = System.currentTimeMillis()
        return when (req) {
            is PeriodReq.Salary ->
                Periods.salaryCycle(salaryDates, req.offset, now)
                    ?: Periods.payCycle(anchor, req.offset, now)
            is PeriodReq.Cycle -> Periods.payCycle(anchor, req.offset, now)
            is PeriodReq.Month -> Periods.month(req.offset, now)
            is PeriodReq.Year -> Periods.year(req.offset, now)
            is PeriodReq.Custom -> Periods.custom(req.startDay, req.endDay)
        }
    }

    /** The period one step earlier than [req], for trend comparison. Null for custom ranges. */
    private fun resolvePrev(req: PeriodReq, anchor: Int, salaryDates: List<Long>): Period? = when (req) {
        is PeriodReq.Salary -> resolve(PeriodReq.Salary(req.offset - 1), anchor, salaryDates)
        is PeriodReq.Cycle -> resolve(PeriodReq.Cycle(req.offset - 1), anchor, salaryDates)
        is PeriodReq.Month -> resolve(PeriodReq.Month(req.offset - 1), anchor, salaryDates)
        is PeriodReq.Year -> resolve(PeriodReq.Year(req.offset - 1), anchor, salaryDates)
        is PeriodReq.Custom -> null
    }

    // ---- navigation ----
    /** Switch top-level tab: resets the back stack to that tab's root. */
    fun selectTab(screen: Screen) {
        if (backStack.value.size == 1 && backStack.value.first() == screen) return
        backStack.value = listOf(screen)
    }
    fun navigate(screen: Screen) { backStack.value = backStack.value + screen }
    fun pop(): Boolean {
        if (backStack.value.size <= 1) return false
        backStack.value = backStack.value.dropLast(1)
        return true
    }
    fun goHome() { backStack.value = listOf(Screen.Dashboard) }

    // ---- period controls ----
    fun setPeriod(req: PeriodReq) { periodReq.value = req }
    fun stepPeriod(delta: Int) {
        periodReq.value = when (val r = periodReq.value) {
            is PeriodReq.Salary -> PeriodReq.Salary(r.offset + delta)
            is PeriodReq.Cycle -> PeriodReq.Cycle(r.offset + delta)
            is PeriodReq.Month -> PeriodReq.Month(r.offset + delta)
            is PeriodReq.Year -> PeriodReq.Year(r.offset + delta)
            is PeriodReq.Custom -> r // custom ranges aren't steppable
        }
    }
    val canStep: Boolean get() = periodReq.value !is PeriodReq.Custom

    // ---- settings ----
    fun setAnchorDay(day: Int) {
        Prefs.setAnchorDay(getApplication<Application>(), day)
        anchorDay.value = day
    }
    fun setInvestAsSpending(v: Boolean) {
        Prefs.setInvestAsSpending(getApplication<Application>(), v)
        investAsSpending.value = v
    }
    fun setSalaryAmount(v: Double) {
        Prefs.setSalaryAmount(getApplication<Application>(), v)
        salaryAmount.value = v
    }
    fun setManualBalance(v: Double) {
        Prefs.setManualBalance(getApplication<Application>(), v)
        manualBalance.value = v
    }

    // ---- data ----
    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            try {
                val added = repo.importFromSms()
                importStatus.value = if (added > 0) "Imported $added new" else null
                pushWidgetSnapshot()
            } catch (e: SecurityException) {
                importStatus.value = "SMS permission needed"
            } catch (e: Exception) {
                importStatus.value = "Couldn't read SMS"
            } finally {
                loading.value = false
            }
        }
    }

    fun setMerchantCategory(merchant: String, category: String, subcategory: String = "") {
        viewModelScope.launch { repo.setMerchantCategory(merchant, category, subcategory) }
    }

    /** Builds the full JSON data export and hands it back on the main thread. */
    fun exportJson(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(repo.exportJson()) }
    }

    /** A human + machine readable summary of exactly what's on screen, for pasting into an AI. */
    fun currentViewSummary(): String {
        val p = period.value
        val t = totals.value
        val ins = insights.value
        val sb = StringBuilder()
        sb.appendLine("SMS Spend — ${p.label}")
        sb.appendLine("Spent: ${fmt(t.spent)} OMR over ${ins.txnCount} transactions")
        sb.appendLine("Income: ${fmt(t.income)} OMR · Net: ${fmt(t.net)} OMR · Invested: ${fmt(t.invested)} OMR")
        if (balance.value > 0) sb.appendLine("Balance: ${fmt(balance.value)} OMR")
        sb.appendLine("Per day: ${fmt(ins.perDay)} OMR (day ${ins.daysElapsed}/${ins.daysTotal})")
        sb.appendLine("Projected this period: ${fmt(ins.projectedSpend)} OMR")
        ins.runwayDays?.let { sb.appendLine("Runway at this pace: ~$it days") }
        if (prevSpent.value > 0) sb.appendLine("Previous period spent: ${fmt(prevSpent.value)} OMR")
        ins.largest?.let { sb.appendLine("Largest: ${it.merchantClean} ${fmt(it.amount)} OMR") }
        ins.busiestDayLabel?.let { sb.appendLine("Busiest day: $it") }
        sb.appendLine()
        sb.appendLine("By category:")
        t.byCategory.forEach { sb.appendLine("  ${it.category}: ${fmt(it.total)} OMR (${it.count})") }
        sb.appendLine()
        sb.appendLine("Top merchants:")
        ins.topMerchants.forEach { sb.appendLine("  ${it.merchant}: ${fmt(it.total)} OMR (${it.count})") }
        return sb.toString()
    }

    private fun pushWidgetSnapshot() {
        val app = getApplication<Application>()
        val t = totals.value
        val ins = insights.value
        val bal = currentBalance.value
        val safe = safeToSpend.value
        val label = period.value.label

        // Cram the headline numbers into the larger widget, then the category bars.
        val summary = buildString {
            if (bal > 0) append("Balance ${fmt2(bal)}")
            if (safe > 0) { if (isNotEmpty()) append(" · "); append("Safe ${fmt2(safe)}/day") }
            append("\nSpent ${fmt2(t.spent)} · ${fmt2(ins.perDay)}/day")
        }
        val bars = t.byCategory.take(4).joinToString("\n") { line ->
            val pct = if (t.spent > 0) (line.total / t.spent * 100).toInt() else 0
            "${line.category}  $pct%  ${"█".repeat((pct + 5) / 10)}"
        }
        val breakdown = (summary + (if (bars.isNotBlank()) "\n$bars" else ""))
            .ifBlank { "No spending yet" }

        val trend = buildString {
            append(fmt2(ins.perDay)).append("/day")
            if (prevSpent.value > 0) {
                val delta = ((ins.projectedSpend - prevSpent.value) / prevSpent.value * 100).toInt()
                val arrow = if (delta >= 0) "▲" else "▼"
                append(" · $arrow${kotlin.math.abs(delta)}% vs last")
            }
        }

        val values = mapOf(
            "spent" to "${fmt2(t.spent)} OMR",
            "income" to "${fmt2(t.income)} OMR",
            "net" to ((if (t.net >= 0) "+" else "−") + fmt2(kotlin.math.abs(t.net)) + " OMR"),
            "invested" to "${fmt2(t.invested)} OMR",
            "balance" to (if (bal > 0) "${fmt2(bal)} OMR" else "—"),
            "safe" to (if (safe > 0) "${fmt2(safe)} OMR" else "—"),
            "perday" to "${fmt2(ins.perDay)} OMR",
            "projected" to "${fmt2(ins.projectedSpend)} OMR"
        )
        Prefs.setWidgetSnapshot(app, values, breakdown, trend, label)
        WidgetUpdater.updateAll(app)
    }

    private fun fmt(v: Double) = String.format("%.3f", v)
    private fun fmt2(v: Double) = String.format(java.util.Locale.US, "%,.2f", v)
}
