package com.example.smsspend

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private val reqPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showFragment(HomeFragment())
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)

        val nav = findViewById<TabLayout>(R.id.bottom_nav)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            reqPerm.launch(Manifest.permission.READ_SMS)
        }

        listOf("🏠 Home", "📋 Activity", "📊 Analytics", "💡 Insights", "📈 Portfolio", "🔮 Retire")
            .forEach { nav.addTab(nav.newTab().setText(it)) }

        showFragment(HomeFragment())

        nav.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val f: Fragment = when (tab.position) {
                    0 -> HomeFragment()
                    1 -> ActivityFragment()
                    2 -> AnalyticsFragment()
                    3 -> InsightsFragment()
                    4 -> PortfolioFragment()
                    5 -> RetirementFragment()
                    else -> return
                }
                showFragment(f)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun showFragment(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, f)
            .commit()
    }

    companion object {
        fun updateWidget(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SpendingWidget::class.java))
            for (id in ids) SpendingWidget.render(ctx, mgr, id)
        }
    }
}
