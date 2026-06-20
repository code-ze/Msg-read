package com.example.smsspend

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TxnAdapter(
    private var items: List<Txn>,
    private val onClick: (Txn) -> Unit
) : RecyclerView.Adapter<TxnAdapter.VH>() {

    private val df = SimpleDateFormat("MM/dd/yy", Locale.US)

    private val catEmoji = mapOf(
        "Food Delivery" to "🛵", "Cafes & Tea" to "☕", "Restaurants" to "🍽️",
        "Groceries" to "🛒", "Fuel & Transport" to "⛽", "Pharmacy & Health" to "💊",
        "Telecom" to "📱", "Utilities" to "⚡", "Subscriptions" to "📲",
        "Online Shopping" to "📦", "Charity" to "🤲", "Transfers" to "↗️",
        "Income" to "💰", "Investments" to "📈", "Other" to "💳"
    )

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: TextView = v.findViewById(R.id.tv_icon)
        val merchant: TextView = v.findViewById(R.id.merchant)
        val cat: TextView = v.findViewById(R.id.cat)
        val amount: TextView = v.findViewById(R.id.amount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_txn, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val t = items[pos]
        h.icon.text = catEmoji[t.category] ?: "💳"
        h.merchant.text = t.merchant
        h.cat.text = "${t.category}  ·  ${df.format(Date(t.date))}"
        val isIncome = t.type == "deposit" || t.type == "walletIn"
        val sign = if (isIncome) "+" else "-"
        h.amount.text = "$sign%.3f".format(t.amount)
        h.amount.setTextColor(if (isIncome) 0xFF00C896.toInt() else 0xFFFF4B6E.toInt())
        h.itemView.setOnClickListener { onClick(t) }
    }

    fun updateData(newItems: List<Txn>) {
        items = newItems; notifyDataSetChanged()
    }
}
