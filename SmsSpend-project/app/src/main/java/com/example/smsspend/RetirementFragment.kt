package com.example.smsspend

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.util.Calendar
import kotlin.math.ln
import kotlin.math.log
import kotlin.math.pow

class RetirementFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_retirement, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.btn_calculate).setOnClickListener { calculate(view) }
    }

    private fun calculate(view: View) {
        val savings = view.findViewById<EditText>(R.id.et_savings).text.toString().toDoubleOrNull() ?: 0.0
        val monthly = view.findViewById<EditText>(R.id.et_monthly).text.toString().toDoubleOrNull() ?: 0.0
        val annualReturn = view.findViewById<EditText>(R.id.et_return).text.toString().toDoubleOrNull() ?: 7.0
        val desiredMonthly = view.findViewById<EditText>(R.id.et_desired).text.toString().toDoubleOrNull() ?: 1000.0
        val swr = view.findViewById<EditText>(R.id.et_swr).text.toString().toDoubleOrNull() ?: 4.0

        val r = annualReturn / 100.0
        val rm = r / 12.0
        val swrMonthly = swr / 100.0 / 12.0

        // FIRE number: how much you need to sustain desired monthly income
        val fireNumber = desiredMonthly / swrMonthly

        // How many months to reach FIRE number
        val months = if (monthly <= 0 && savings >= fireNumber) {
            0.0
        } else if (rm == 0.0) {
            if (monthly > 0) (fireNumber - savings) / monthly else Double.POSITIVE_INFINITY
        } else {
            // FV = savings * (1+rm)^n + monthly * [(1+rm)^n - 1] / rm = fireNumber
            // Solve for n numerically (simplified closed-form approximation)
            solveMonths(savings, monthly, rm, fireNumber)
        }

        val years = months / 12.0
        val wholeYears = years.toInt()
        val remMonths = ((years - wholeYears) * 12).toInt()

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val retireYear = currentYear + wholeYears + if (remMonths > 0) 1 else 0

        // Projected value if contributing for `months` months
        val projectedPortfolio = fv(savings, monthly, rm, months)
        val projectedMonthlyIncome = projectedPortfolio * swrMonthly
        val progress = if (fireNumber > 0) minOf(savings / fireNumber * 100, 100.0) else 0.0

        // How much you need to invest monthly to reach FIRE in a given time (10y / 20y / 30y)
        val neededFor10y = monthlyNeeded(savings, rm, fireNumber, 120.0)
        val neededFor20y = monthlyNeeded(savings, rm, fireNumber, 240.0)
        val neededFor30y = monthlyNeeded(savings, rm, fireNumber, 360.0)

        // Show results
        view.findViewById<View>(R.id.results_section).visibility = View.VISIBLE

        view.findViewById<TextView>(R.id.tv_fire_number).text = "%.0f".format(fireNumber)
        view.findViewById<TextView>(R.id.tv_fire_sub).text =
            "Generates %.0f OMR/month at %.0f%% withdrawal rate".format(desiredMonthly, swr)

        view.findViewById<TextView>(R.id.tv_time_to_fire).text = when {
            months.isInfinite() || months > 99 * 12 -> "Never at this rate"
            months == 0.0 -> "Already there!"
            else -> "${wholeYears}y ${remMonths}m"
        }
        view.findViewById<TextView>(R.id.tv_retire_year).text = "Retire in ~$retireYear"

        view.findViewById<TextView>(R.id.tv_monthly_needed).text =
            "%.0f".format(neededFor20y.coerceAtLeast(0.0))

        view.findViewById<TextView>(R.id.tv_proj_portfolio).text = "%.0f OMR".format(projectedPortfolio)
        view.findViewById<TextView>(R.id.tv_proj_income).text = "%.0f OMR/mo".format(projectedMonthlyIncome)
        view.findViewById<TextView>(R.id.tv_progress).text = "%.0f%%".format(progress)

        val milestones = buildString {
            append("Current savings: %.0f OMR (%.0f%% of FIRE)\n\n".format(savings, progress))
            if (!neededFor10y.isNaN() && neededFor10y > 0)
                append("• To FIRE in 10 years: invest %.0f OMR/month\n".format(neededFor10y))
            if (!neededFor20y.isNaN() && neededFor20y > 0)
                append("• To FIRE in 20 years: invest %.0f OMR/month\n".format(neededFor20y))
            if (!neededFor30y.isNaN() && neededFor30y > 0)
                append("• To FIRE in 30 years: invest %.0f OMR/month\n".format(neededFor30y))
            append("\n📈 At %.0f%% annual return".format(annualReturn))
            append("\n💰 FIRE number = monthly income × 12 ÷ SWR")
            append("\n🔄 Using %.0f%% safe withdrawal rate".format(swr))
        }
        view.findViewById<TextView>(R.id.tv_milestones).text = milestones.trim()
    }

    private fun fv(pv: Double, pmt: Double, rm: Double, n: Double): Double {
        return if (rm == 0.0) pv + pmt * n
        else pv * (1 + rm).pow(n) + pmt * ((1 + rm).pow(n) - 1) / rm
    }

    private fun solveMonths(pv: Double, pmt: Double, rm: Double, target: Double): Double {
        if (pmt <= 0 && pv >= target) return 0.0
        if (pmt <= 0) return Double.POSITIVE_INFINITY
        return try {
            // Closed-form: n = ln((pmt + target*rm) / (pmt + pv*rm)) / ln(1 + rm)
            ln((pmt + target * rm) / (pmt + pv * rm)) / ln(1 + rm)
        } catch (e: Exception) { Double.POSITIVE_INFINITY }
    }

    private fun monthlyNeeded(pv: Double, rm: Double, target: Double, n: Double): Double {
        if (pv >= target) return 0.0
        return if (rm == 0.0) (target - pv) / n
        else {
            val factor = (1 + rm).pow(n)
            (target - pv * factor) * rm / (factor - 1)
        }
    }
}
