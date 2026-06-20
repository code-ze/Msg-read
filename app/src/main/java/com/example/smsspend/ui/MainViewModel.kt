package com.example.smsspend.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsspend.data.Prefs
import com.example.smsspend.data.Repository
import com.example.smsspend.data.TxnEntity
import com.example.smsspend.model.Period
import com.example.smsspend.model.Periods
import com.example.smsspend.model.Totals
import com.example.smsspend.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A request for a period; resolved to a concrete [Period] using the current anchor day. */
sealed interface PeriodReq {
    data class Cycle(val offset: Int) : PeriodReq
    data class Month(val offset: Int) : PeriodReq
    data class Year(val offset: Int) : PeriodReq
    data class Custom(val startDay: Long, val endDay: Long) : PeriodReq
}

sealed interface Screen {
    data object Dashboard : Screen
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

    val periodReq = MutableStateFlow<PeriodReq>(PeriodReq.Cycle(0))

    val importStatus = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(false)

    val period: StateFlow<Period> =
        combine(periodReq, anchorDay) { req, anchor -> resolve(req, anchor) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, resolve(periodReq.value, anchorDay.value))

    private val periodTxns: StateFlow<List<TxnEntity>> =
        period.flatMapLatest { p -> repo.txnsInPeriod(p.start, p.end) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totals: StateFlow<Totals> =
        combine(periodTxns, investAsSpending) { txns, invest -> Totals.from(txns, invest) }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5000),
                Totals(0.0, 0.0, 0.0, 0.0, emptyList())
            )

    val recentTxns: StateFlow<List<TxnEntity>> = periodTxns

    fun txnsForCategory(start: Long, end: Long, category: String) =
        repo.txnsForCategory(start, end, category)

    fun txnsForMerchant(merchant: String) = repo.txnsForMerchant(merchant)

    private fun resolve(req: PeriodReq, anchor: Int): Period {
        val now = System.currentTimeMillis()
        return when (req) {
            is PeriodReq.Cycle -> Periods.payCycle(anchor, req.offset, now)
            is PeriodReq.Month -> Periods.month(req.offset, now)
            is PeriodReq.Year -> Periods.year(req.offset, now)
            is PeriodReq.Custom -> Periods.custom(req.startDay, req.endDay)
        }
    }

    // ---- navigation ----
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

    fun setMerchantCategory(merchant: String, category: String) {
        viewModelScope.launch { repo.setMerchantCategory(merchant, category) }
    }

    private fun pushWidgetSnapshot() {
        val app = getApplication<Application>()
        val t = totals.value
        val label = period.value.label
        val breakdown = t.byCategory.take(4)
            .joinToString("\n") { "• ${it.category}  ${fmt(it.total)}" }
            .ifBlank { "No spending yet" }
        Prefs.setWidgetSnapshot(
            app,
            spent = fmt(t.spent) + " OMR",
            income = fmt(t.income) + " OMR",
            net = (if (t.net >= 0) "+" else "−") + fmt(kotlin.math.abs(t.net)) + " OMR",
            invested = fmt(t.invested) + " OMR",
            breakdown = breakdown,
            label = label
        )
        WidgetUpdater.updateAll(app)
    }

    private fun fmt(v: Double) = String.format("%.3f", v)
}
