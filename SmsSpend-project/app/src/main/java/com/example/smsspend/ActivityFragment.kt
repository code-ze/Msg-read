package com.example.smsspend

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ActivityFragment : Fragment() {

    private var all: List<Txn> = emptyList()
    private lateinit var adapter: TxnAdapter
    private var filter = "ALL"

    private val cats = listOf(
        "Food Delivery", "Cafes & Tea", "Restaurants", "Groceries",
        "Fuel & Transport", "Pharmacy & Health", "Telecom", "Utilities",
        "Subscriptions", "Online Shopping", "Charity", "Transfers", "Income",
        "Investments", "Other"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_activity, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = TxnAdapter(emptyList()) { t -> recategorize(t) }
        recycler.adapter = adapter

        view.findViewById<TextView>(R.id.btn_refresh).setOnClickListener { load() }

        view.findViewById<TextView>(R.id.chip_all).setOnClickListener {
            filter = "ALL"; applyFilter(view)
        }
        view.findViewById<TextView>(R.id.chip_income).setOnClickListener {
            filter = "INCOME"; applyFilter(view)
        }
        view.findViewById<TextView>(R.id.chip_expense).setOnClickListener {
            filter = "EXPENSE"; applyFilter(view)
        }

        load()
    }

    private fun load() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return
        val ov = Store.overrides(requireContext())
        all = SmsParser.load(requireContext()).map { t -> ov[t.key]?.let { t.copy(category = it) } ?: t }
        applyFilter(requireView())
    }

    private fun applyFilter(view: View) {
        val filtered = when (filter) {
            "INCOME" -> all.filter { it.type == "deposit" || it.type == "walletIn" }
            "EXPENSE" -> all.filter { it.type == "debit" || it.type == "walletOut" }
            else -> all
        }
        adapter.updateData(filtered)

        val chipAll = view.findViewById<TextView>(R.id.chip_all)
        val chipInc = view.findViewById<TextView>(R.id.chip_income)
        val chipExp = view.findViewById<TextView>(R.id.chip_expense)

        chipAll.setBackgroundColor(if (filter == "ALL") 0xFF00C896.toInt() else 0xFF161B22.toInt())
        chipAll.setTextColor(if (filter == "ALL") 0xFF0D1117.toInt() else 0xFF00C896.toInt())
        chipInc.setBackgroundColor(if (filter == "INCOME") 0xFF00C896.toInt() else 0xFF161B22.toInt())
        chipInc.setTextColor(if (filter == "INCOME") 0xFF0D1117.toInt() else 0xFF00C896.toInt())
        chipExp.setBackgroundColor(if (filter == "EXPENSE") 0xFFFF4B6E.toInt() else 0xFF161B22.toInt())
        chipExp.setTextColor(if (filter == "EXPENSE") 0xFF0D1117.toInt() else 0xFF8B949E.toInt())
    }

    private fun recategorize(t: Txn) {
        AlertDialog.Builder(requireContext())
            .setTitle(t.merchant)
            .setItems(cats.toTypedArray()) { _, i ->
                Store.setOverride(requireContext(), t.key, cats[i]); load()
            }.show()
    }
}
