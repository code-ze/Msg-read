package com.example.smsspend

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsFragment : Fragment() {

    private var all: List<Txn> = emptyList()
    private var periodIdx = 0
    private val periods = arrayOf("This Month", "Last 3 Months", "Last 6 Months", "All Time")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_analytics, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tv_period).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Period")
                .setItems(periods) { _, i -> periodIdx = i; render(view) }
                .show()
        }

        load(view)
    }

    private fun load(view: View) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return
        val ov = Store.overrides(requireContext())
        all = SmsParser.load(requireContext()).map { t -> ov[t.key]?.let { t.copy(category = it) } ?: t }
        render(view)
    }

    private fun render(view: View) {
        view.findViewById<TextView>(R.id.tv_period).text = "${periods[periodIdx]} ▾"
        val cutoff = when (periodIdx) {
            0 -> { val c = Calendar.getInstance(); c.set(Calendar.DAY_OF_MONTH, 1)
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.timeInMillis }
            1 -> System.currentTimeMillis() - 90L * 86400000
            2 -> System.currentTimeMillis() - 180L * 86400000
            else -> 0L
        }
        val data = if (cutoff == 0L) all else all.filter { it.date >= cutoff }
        val expenses = data.filter { it.type == "debit" || it.type == "walletOut" }
        val total = expenses.sumOf { it.amount }

        // Pie chart
        val byCat = expenses.groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }

        val pieChart = view.findViewById<PieChartView>(R.id.pie_chart)
        pieChart.slices = byCat.mapIndexed { i, (name, amt) ->
            PieChartView.Slice(name, amt.toFloat(), PieChartView.CHART_COLORS[i % PieChartView.CHART_COLORS.size])
        }

        // Category recycler
        val catRecycler = view.findViewById<RecyclerView>(R.id.recycler_categories)
        catRecycler.layoutManager = LinearLayoutManager(requireContext())
        catRecycler.adapter = CategoryAdapter(byCat, total)

        // Bar chart (monthly)
        val mf = SimpleDateFormat("MMM", Locale.US)
        val byMonth = expenses.groupBy { SimpleDateFormat("yyyy-MM", Locale.US).format(Date(it.date)) }
            .entries.sortedBy { it.key }
            .takeLast(6)
            .map { (key, txns) ->
                val lbl = try { mf.format(SimpleDateFormat("yyyy-MM", Locale.US).parse(key)!!) } catch (e: Exception) { key }
                BarChartView.Bar(lbl, txns.sumOf { it.amount }.toFloat())
            }
        view.findViewById<BarChartView>(R.id.bar_chart).bars = byMonth
    }

    inner class CategoryAdapter(
        private val items: List<Map.Entry<String, Double>>,
        private val total: Double
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tv_cat_name)
            val pct: TextView = v.findViewById(R.id.tv_cat_pct)
            val amount: TextView = v.findViewById(R.id.tv_cat_amount)
            val bar: ProgressBar = v.findViewById(R.id.progress)
        }
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val (name, amt) = items[pos]
            val pct = if (total > 0) (amt / total * 100).toInt() else 0
            h.name.text = name
            h.pct.text = "$pct%"
            h.amount.text = "%.0f OMR".format(amt)
            h.bar.progress = pct
            h.bar.progressTintList = android.content.res.ColorStateList.valueOf(
                PieChartView.CHART_COLORS[pos % PieChartView.CHART_COLORS.size])
        }
    }
}
