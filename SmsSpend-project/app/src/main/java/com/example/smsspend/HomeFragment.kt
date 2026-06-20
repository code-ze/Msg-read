package com.example.smsspend

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var tvBalance: TextView
    private lateinit var tvBalanceSub: TextView
    private lateinit var tvIncome: TextView
    private lateinit var tvSpent: TextView
    private lateinit var tvNet: TextView
    private lateinit var tvInvested: TextView
    private lateinit var chart: BalanceChartView
    private lateinit var selPanel: View
    private lateinit var tvSelDate: TextView
    private lateinit var tvSelBalance: TextView
    private lateinit var tvSelChange: TextView
    private lateinit var btnSeeTxns: TextView
    private lateinit var tvPerDay: TextView
    private lateinit var tvBiggestDay: TextView
    private lateinit var tvBiggestDayDate: TextView
    private lateinit var tvBestMonth: TextView
    private lateinit var tvWorstMonth: TextView
    private lateinit var tvRange: TextView

    private var allData: List<Txn> = emptyList()
    private var chartPoints: List<BalanceChartView.Point> = emptyList()
    private var selectedPoint: BalanceChartView.Point? = null

    // Range: 0=All, 1=1y, 2=6m, 3=3m, 4=1m
    private var rangeIdx = 0
    private val rangeLabels = arrayOf("All Time", "Last 12 Months", "Last 6 Months", "Last 3 Months", "This Month")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvBalance = view.findViewById(R.id.tv_balance)
        tvBalanceSub = view.findViewById(R.id.tv_balance_sub)
        tvIncome = view.findViewById(R.id.tv_income)
        tvSpent = view.findViewById(R.id.tv_spent)
        tvNet = view.findViewById(R.id.tv_net)
        tvInvested = view.findViewById(R.id.tv_invested)
        chart = view.findViewById(R.id.chart)
        selPanel = view.findViewById(R.id.selection_panel)
        tvSelDate = view.findViewById(R.id.tv_sel_date)
        tvSelBalance = view.findViewById(R.id.tv_sel_balance)
        tvSelChange = view.findViewById(R.id.tv_sel_change)
        btnSeeTxns = view.findViewById(R.id.btn_see_txns)
        tvPerDay = view.findViewById(R.id.tv_per_day)
        tvBiggestDay = view.findViewById(R.id.tv_biggest_day)
        tvBiggestDayDate = view.findViewById(R.id.tv_biggest_day_date)
        tvBestMonth = view.findViewById(R.id.tv_best_month)
        tvWorstMonth = view.findViewById(R.id.tv_worst_month)
        tvRange = view.findViewById(R.id.tv_range)

        view.findViewById<TextView>(R.id.btn_prev).setOnClickListener {
            rangeIdx = (rangeIdx - 1 + rangeLabels.size) % rangeLabels.size
            applyRange()
        }
        view.findViewById<TextView>(R.id.btn_next).setOnClickListener {
            rangeIdx = (rangeIdx + 1) % rangeLabels.size
            applyRange()
        }
        tvRange.setOnClickListener {
            rangeIdx = (rangeIdx + 1) % rangeLabels.size
            applyRange()
        }

        chart.onPointSelected = { pt ->
            selectedPoint = pt
            showSelectionPanel(pt)
        }

        btnSeeTxns.setOnClickListener {
            selectedPoint?.let { showDipSheet(it) }
        }

        loadData()
    }

    private fun loadData() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            tvBalance.text = "0"
            tvBalanceSub.text = "Grant SMS permission to load data"
            return
        }
        val ov = Store.overrides(requireContext())
        allData = SmsParser.load(requireContext()).map { t -> ov[t.key]?.let { t.copy(category = it) } ?: t }
        applyRange()
    }

    private fun applyRange() {
        tvRange.text = rangeLabels[rangeIdx]
        val cutoff = when (rangeIdx) {
            0 -> 0L
            1 -> System.currentTimeMillis() - 365L * 86400000
            2 -> System.currentTimeMillis() - 180L * 86400000
            3 -> System.currentTimeMillis() - 90L * 86400000
            4 -> {
                val c = Calendar.getInstance()
                c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0)
                c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.timeInMillis
            }
            else -> 0L
        }
        val filtered = if (cutoff == 0L) allData else allData.filter { it.date >= cutoff }
        render(filtered)
    }

    private fun render(data: List<Txn>) {
        val expenses = data.filter { it.type == "debit" || it.type == "walletOut" }
        val income = data.filter { it.type == "deposit" || it.type == "walletIn" }
        val invested = data.filter { it.category == "Investments" }

        val totalSpent = expenses.sumOf { it.amount }
        val totalIncome = income.sumOf { it.amount }
        val totalInvested = invested.sumOf { it.amount }
        val net = totalIncome - totalSpent

        tvIncome.text = "%.2f".format(totalIncome)
        tvSpent.text = "%.2f".format(totalSpent)
        tvInvested.text = "%.2f".format(totalInvested)

        val netColor = if (net >= 0) 0xFF00C896.toInt() else 0xFFFF4B6E.toInt()
        tvNet.text = "%.2f".format(net)
        tvNet.setTextColor(netColor)

        tvBalance.text = "%.0f".format(net)
        tvBalance.setTextColor(netColor)

        // Quick insights
        val days = daySpan(data)
        val perDay = if (days > 0) totalSpent / days else 0.0
        tvPerDay.text = "%.2f".format(perDay)

        val byDay = expenses.groupBy { dayKey(it.date) }
        val biggestDay = byDay.maxByOrNull { (_, v) -> v.sumOf { it.amount } }
        if (biggestDay != null) {
            tvBiggestDay.text = "%.0f".format(biggestDay.value.sumOf { it.amount })
            tvBiggestDayDate.text = SimpleDateFormat("MMM d, yy", Locale.US).format(Date(biggestDay.value.first().date))
        }

        val byMonth = expenses.groupBy { monthKey(it.date) }
        val monthAmts = byMonth.mapValues { (_, v) -> v.sumOf { it.amount } }
        tvBestMonth.text = monthAmts.minByOrNull { it.value }?.let { "${it.key}\n%.0f OMR".format(it.value) } ?: "—"
        tvWorstMonth.text = monthAmts.maxByOrNull { it.value }?.let { "${it.key}\n%.0f OMR".format(it.value) } ?: "—"

        buildChart(data)

        // Update widget
        val lines = StringBuilder()
        val catMap = HashMap<String, Double>()
        for (t in expenses) catMap[t.category] = (catMap[t.category] ?: 0.0) + t.amount
        for ((c, a) in catMap.entries.sortedByDescending { it.value }.take(4)) {
            lines.append("• $c  %.0f\n".format(a))
        }
        Store.saveSummary(requireContext(), lines.toString().trim(), "%.0f OMR".format(totalSpent))
        MainActivity.updateWidget(requireContext())
    }

    private fun buildChart(data: List<Txn>) {
        val sorted = data.sortedBy { it.date }
        var runBal = 0.0
        val points = mutableListOf<BalanceChartView.Point>()
        val dipIndices = mutableSetOf<Int>()

        sorted.forEach { t ->
            when (t.type) {
                "deposit", "walletIn" -> runBal += t.amount
                "debit", "walletOut" -> runBal -= t.amount
            }
            val idx = points.size
            points += BalanceChartView.Point(t.date, runBal)
            if (points.size > 1) {
                val prev = points[idx - 1].balance
                if (runBal < prev - 50) dipIndices += idx
            }
        }

        chartPoints = points
        chart.dataPoints = points
        chart.dipIndices = dipIndices

        if (sorted.isNotEmpty()) {
            val df = SimpleDateFormat("MMM d, yyyy", Locale.US)
            tvBalanceSub.text = "${df.format(Date(sorted.first().date))} – ${df.format(Date(sorted.last().date))}"
        }
    }

    private fun showSelectionPanel(pt: BalanceChartView.Point) {
        selPanel.visibility = View.VISIBLE
        val df = SimpleDateFormat("EEEE, MMM d yyyy", Locale.US)
        tvSelDate.text = df.format(Date(pt.timestamp))
        tvSelBalance.text = "%.3f OMR".format(pt.balance)

        val idx = chartPoints.indexOf(pt)
        if (idx > 0) {
            val prevBal = chartPoints[idx - 1].balance
            val diff = pt.balance - prevBal
            tvSelChange.text = (if (diff >= 0) "+" else "") + "%.3f OMR from previous".format(diff)
            tvSelChange.setTextColor(if (diff >= 0) 0xFF00C896.toInt() else 0xFFFF4B6E.toInt())
        } else {
            tvSelChange.text = "Starting point"
            tvSelChange.setTextColor(0xFF8B949E.toInt())
        }
    }

    private fun showDipSheet(pt: BalanceChartView.Point) {
        val ctx = requireContext()
        val dialog = BottomSheetDialog(ctx)
        val v = layoutInflater.inflate(R.layout.bottom_sheet_dip, null)
        dialog.setContentView(v)

        val dayStr = dayKey(pt.timestamp)
        val dayTxns = allData.filter { dayKey(it.date) == dayStr }

        val df = SimpleDateFormat("EEEE, MMM d yyyy", Locale.US)
        v.findViewById<TextView>(R.id.tv_sheet_date).text = df.format(Date(pt.timestamp))
        v.findViewById<TextView>(R.id.tv_sheet_balance).text = "Balance: %.3f OMR".format(pt.balance)

        val change = run {
            val idx = chartPoints.indexOfFirst { it.timestamp == pt.timestamp }
            if (idx > 0) {
                val diff = pt.balance - chartPoints[idx - 1].balance
                (if (diff >= 0) "+" else "") + "%.3f OMR".format(diff)
            } else "—"
        }
        val tvChange = v.findViewById<TextView>(R.id.tv_sheet_change)
        tvChange.text = change

        val recycler = v.findViewById<RecyclerView>(R.id.sheet_recycler)
        val empty = v.findViewById<TextView>(R.id.tv_sheet_empty)

        if (dayTxns.isEmpty()) {
            recycler.visibility = View.GONE
            empty.visibility = View.VISIBLE
            v.findViewById<TextView>(R.id.tv_sheet_total).text = ""
        } else {
            recycler.visibility = View.VISIBLE
            empty.visibility = View.GONE
            recycler.layoutManager = LinearLayoutManager(ctx)
            recycler.adapter = TxnAdapter(dayTxns) {}
            val daySpent = dayTxns.filter { it.type == "debit" || it.type == "walletOut" }.sumOf { it.amount }
            val dayInc = dayTxns.filter { it.type == "deposit" || it.type == "walletIn" }.sumOf { it.amount }
            v.findViewById<TextView>(R.id.tv_sheet_total).text =
                "Day total: -%.3f OMR spent, +%.3f OMR received".format(daySpent, dayInc)
        }

        dialog.show()
    }

    private fun dayKey(ts: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ts))
    private fun monthKey(ts: Long) = SimpleDateFormat("MMM yyyy", Locale.US).format(Date(ts))
    private fun daySpan(txns: List<Txn>): Int {
        if (txns.isEmpty()) return 1
        return maxOf(((txns.maxOf { it.date } - txns.minOf { it.date }) / 86400000L).toInt(), 1)
    }
}
