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
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val reqPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showFragment(HomeFragment())
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            reqPerm.launch(Manifest.permission.READ_SMS)
        }

        showFragment(HomeFragment())

        nav.setOnItemSelectedListener { item ->
            val f: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_activity -> ActivityFragment()
                R.id.nav_analytics -> AnalyticsFragment()
                R.id.nav_insights -> InsightsFragment()
                R.id.nav_portfolio -> PortfolioFragment()
                R.id.nav_retire -> RetirementFragment()
                else -> return@setOnItemSelectedListener false
            }
            showFragment(f)
            true
        }
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
