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

class InsightsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_insights, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return

        val ov = Store.overrides(requireContext())
        val all = SmsParser.load(requireContext()).map { t -> ov[t.key]?.let { t.copy(category = it) } ?: t }
        val insights = InsightsEngine.compute(all)

        view.findViewById<TextView>(R.id.tv_insight_count).text = "${insights.size} insights"

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = InsightAdapter(insights)
    }

    inner class InsightAdapter(private val items: List<Insight>) :
        RecyclerView.Adapter<InsightAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val emoji: TextView = v.findViewById(R.id.tv_emoji)
            val title: TextView = v.findViewById(R.id.tv_title)
            val value: TextView = v.findViewById(R.id.tv_value)
            val sub: TextView = v.findViewById(R.id.tv_sub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_insight, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val ins = items[pos]
            h.emoji.text = ins.emoji
            h.title.text = ins.title
            h.value.text = ins.value
            h.sub.text = ins.sub

            if (ins.highlight) {
                h.itemView.setBackgroundColor(0xFF1C2128.toInt())
            } else {
                h.itemView.setBackgroundColor(0xFF161B22.toInt())
            }

            if (ins.detail.isNotEmpty()) {
                h.itemView.setOnClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle("${ins.emoji} ${ins.title}")
                        .setMessage(ins.detail)
                        .setPositiveButton("Close", null)
                        .show()
                }
            }
        }
    }
}
