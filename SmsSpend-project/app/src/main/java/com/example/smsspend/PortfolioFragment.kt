package com.example.smsspend

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class PortfolioFragment : Fragment() {

    private var holdings: MutableList<StockHolding> = mutableListOf()
    private val livePrice = HashMap<String, StockPrice>()
    private lateinit var adapter: StockAdapter
    private lateinit var tvPortfolioValue: TextView
    private lateinit var tvPnl: TextView
    private lateinit var tvMsxStatus: TextView
    private lateinit var msxDot: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_portfolio, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvPortfolioValue = view.findViewById(R.id.tv_portfolio_value)
        tvPnl = view.findViewById(R.id.tv_portfolio_pnl)
        tvMsxStatus = view.findViewById(R.id.tv_msx_status)
        msxDot = view.findViewById(R.id.msx_dot)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = StockAdapter()
        recycler.adapter = adapter

        view.findViewById<TextView>(R.id.btn_add_stock).setOnClickListener { showAddDialog() }
        view.findViewById<TextView>(R.id.btn_refresh_prices).setOnClickListener { refreshAllPrices() }

        holdings = Store.loadPortfolio(requireContext())
        adapter.notifyDataSetChanged()
        updateTotals()

        if (holdings.isNotEmpty()) refreshAllPrices()
    }

    private fun refreshAllPrices() {
        tvMsxStatus.text = "Fetching MSX prices…"
        msxDot.setBackgroundColor(0xFFFBBF24.toInt())

        val ctx = requireContext()
        lifecycleScope.launch {
            var success = 0; var fail = 0
            for (h in holdings) {
                MsxService.getPrice(h.symbol).fold(
                    onSuccess = { sp -> livePrice[h.symbol] = sp; success++ },
                    onFailure = { fail++ }
                )
            }
            if (isAdded) {
                adapter.notifyDataSetChanged()
                updateTotals()
                if (fail == 0 && success > 0) {
                    tvMsxStatus.text = "MSX prices live • $success stocks updated"
                    msxDot.setBackgroundColor(0xFF00C896.toInt())
                } else if (success > 0) {
                    tvMsxStatus.text = "Partial: $success updated, $fail unavailable"
                    msxDot.setBackgroundColor(0xFFFBBF24.toInt())
                } else {
                    tvMsxStatus.text = "MSX unavailable — check connection"
                    msxDot.setBackgroundColor(0xFFFF4B6E.toInt())
                }
            }
        }
    }

    private fun updateTotals() {
        var totalValue = 0.0; var totalCost = 0.0
        for (h in holdings) {
            val price = livePrice[h.symbol]?.price ?: h.avgPrice
            totalValue += price * h.shares
            totalCost += h.avgPrice * h.shares
        }
        val pnl = totalValue - totalCost
        tvPortfolioValue.text = if (holdings.isEmpty()) "—" else "%.3f".format(totalValue)
        tvPnl.text = (if (pnl >= 0) "+" else "") + "%.3f OMR".format(pnl)
        tvPnl.setTextColor(if (pnl >= 0) 0xFF00C896.toInt() else 0xFFFF4B6E.toInt())
    }

    private fun showAddDialog() {
        val ctx = requireContext()
        val dlg = AlertDialog.Builder(ctx)
        val v = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        fun field(hint: String, num: Boolean = false) = EditText(ctx).apply {
            this.hint = hint; textSize = 15f
            if (num) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setHintTextColor(0xFF8B949E.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        val etSymbol = field("Stock Symbol (e.g. BKDB)").also { v.addView(it) }
        val etName = field("Company Name").also { v.addView(it) }
        val etShares = field("Number of Shares", num = true).also { v.addView(it) }
        val etAvg = field("Average Buy Price (OMR)", num = true).also { v.addView(it) }

        dlg.setTitle("Add Stock")
            .setView(v)
            .setPositiveButton("Add") { _, _ ->
                val sym = etSymbol.text.toString().trim().uppercase()
                val name = etName.text.toString().trim()
                val shares = etShares.text.toString().toDoubleOrNull() ?: 0.0
                val avg = etAvg.text.toString().toDoubleOrNull() ?: 0.0
                if (sym.isNotEmpty() && shares > 0) {
                    holdings.add(StockHolding(sym, name.ifEmpty { sym }, shares, avg))
                    Store.savePortfolio(ctx, holdings)
                    adapter.notifyDataSetChanged()
                    updateTotals()
                    refreshAllPrices()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteDialog(pos: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove ${holdings[pos].symbol}?")
            .setPositiveButton("Remove") { _, _ ->
                holdings.removeAt(pos)
                Store.savePortfolio(requireContext(), holdings)
                adapter.notifyItemRemoved(pos)
                updateTotals()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class StockAdapter : RecyclerView.Adapter<StockAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val symbol: TextView = v.findViewById(R.id.tv_symbol)
            val name: TextView = v.findViewById(R.id.tv_name)
            val shares: TextView = v.findViewById(R.id.tv_shares)
            val avgPrice: TextView = v.findViewById(R.id.tv_avg_price)
            val currentPrice: TextView = v.findViewById(R.id.tv_current_price)
            val pnl: TextView = v.findViewById(R.id.tv_pnl)
            val pnlPct: TextView = v.findViewById(R.id.tv_pnl_pct)
        }
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_stock, parent, false))
        override fun getItemCount() = holdings.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val holding = holdings[pos]
            val live = livePrice[holding.symbol]
            val curPrice = live?.price ?: holding.avgPrice
            val pnl = (curPrice - holding.avgPrice) * holding.shares
            val pnlPct = if (holding.avgPrice > 0) (curPrice - holding.avgPrice) / holding.avgPrice * 100 else 0.0

            h.symbol.text = holding.symbol
            h.name.text = holding.name
            h.shares.text = "%.0f shares".format(holding.shares)
            h.avgPrice.text = "Avg: %.4f".format(holding.avgPrice)
            h.currentPrice.text = if (live != null) "%.4f".format(curPrice) else "—"
            h.currentPrice.setTextColor(if (live != null) 0xFFFFFFFF.toInt() else 0xFF8B949E.toInt())
            h.pnl.text = (if (pnl >= 0) "+" else "") + "%.3f".format(pnl)
            h.pnl.setTextColor(if (pnl >= 0) 0xFF00C896.toInt() else 0xFFFF4B6E.toInt())
            h.pnlPct.text = "(${if (pnlPct >= 0) "+" else ""}%.1f%%)".format(pnlPct)
            h.pnlPct.setTextColor(if (pnlPct >= 0) 0xFF00C896.toInt() else 0xFFFF4B6E.toInt())

            h.itemView.setOnLongClickListener { showDeleteDialog(pos); true }
            h.itemView.setOnClickListener {
                lifecycleScope.launch {
                    tvMsxStatus.text = "Refreshing ${holding.symbol}…"
                    MsxService.clearCache()
                    MsxService.getPrice(holding.symbol).fold(
                        onSuccess = { sp ->
                            livePrice[holding.symbol] = sp
                            if (isAdded) {
                                adapter.notifyItemChanged(pos)
                                updateTotals()
                                tvMsxStatus.text = "${holding.symbol}: %.4f OMR".format(sp.price)
                                msxDot.setBackgroundColor(0xFF00C896.toInt())
                            }
                        },
                        onFailure = {
                            if (isAdded) {
                                tvMsxStatus.text = "Could not fetch ${holding.symbol} price"
                                msxDot.setBackgroundColor(0xFFFF4B6E.toInt())
                            }
                        }
                    )
                }
            }
        }
    }
}
