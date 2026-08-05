package com.example.smsspend.model

import com.example.smsspend.parser.CardPaymentInfo
import com.example.smsspend.parser.ParsedTxn
import com.example.smsspend.parser.SmsParser

/**
 * Post-processing for credit-card transactions, which arrive as independent SMS but only make
 * sense as a sequence.
 *
 * Two problems this solves:
 *
 * 1. **Foreign currency.** "Card … used for USD 790.420 at Cars on Booking" tells you the billed
 *    amount but not what the bank actually took in rials. A static FX rate is close but never
 *    exact (the cross-currency markup varies). However every card SMS also reports
 *    "Available limit OMR X", so the true charge is simply how far that limit dropped:
 *
 *        charge = previousLimit + paymentsInBetween − currentLimit
 *
 *    Payments have to be added back because they push the limit up. When the arithmetic gives
 *    something implausible (a missed SMS, a reversal, an out-of-order delivery) we keep the
 *    rate-table estimate rather than record a number we can't justify.
 *
 * 2. **Duplicate notifications.** The bank sometimes re-sends a spend SMS minutes later with a
 *    new timestamp. The body differs, so the de-dup key differs, and the amount would be counted
 *    twice. The giveaway is that the available limit is unchanged — a genuine second charge would
 *    have pushed it down again.
 *
 * Pure Kotlin so both behaviours are unit-tested.
 */
object CardCharges {

    /** A derived charge outside this band of the FX estimate is treated as a broken chain. */
    private const val MIN_RATIO = 0.4
    private const val MAX_RATIO = 2.5

    /** Runs de-duplication and then FX reconciliation. */
    fun process(txns: List<ParsedTxn>, payments: List<CardPaymentInfo>): List<ParsedTxn> =
        reconcile(dedupe(txns), payments)

    /**
     * Drops repeat card notifications: same card, merchant, currency and billed amount, with an
     * unchanged available limit. The earliest one is kept.
     */
    fun dedupe(txns: List<ParsedTxn>): List<ParsedTxn> {
        val seen = HashSet<String>()
        // Oldest first so the notification we keep is the original, not the re-send.
        val ordered = txns.sortedBy { it.date }
        val dropped = HashSet<String>()
        for (t in ordered) {
            if (!t.isCard || t.availableLimit <= 0.0) continue
            val billed = if (t.originalAmount > 0.0) t.originalAmount else t.amount
            val sig = "${t.cardLast4}|${t.merchantRaw}|${t.currency}|$billed|${t.availableLimit}"
            if (!seen.add(sig)) dropped.add(t.key)
        }
        return if (dropped.isEmpty()) txns else txns.filter { it.key !in dropped }
    }

    /**
     * Replaces the FX-estimated amount of each foreign card transaction with the exact rial
     * figure implied by the drop in available limit, where that can be established.
     */
    fun reconcile(txns: List<ParsedTxn>, payments: List<CardPaymentInfo>): List<ParsedTxn> {
        val exact = HashMap<String, Double>()

        txns.filter { it.isCard && it.availableLimit > 0.0 }
            .groupBy { it.cardLast4 }
            .forEach { (card, list) ->
                val ordered = list.sortedBy { it.date }
                val cardPayments = payments.filter { it.cardLast4 == card }

                for (i in 1 until ordered.size) {
                    val prev = ordered[i - 1]
                    val cur = ordered[i]
                    if (!cur.isForeign) continue // domestic amounts are already exact

                    val paidBetween = cardPayments
                        .filter { it.date > prev.date && it.date <= cur.date }
                        .sumOf { it.amount }

                    val derived = prev.availableLimit + paidBetween - cur.availableLimit
                    if (derived <= 0.0) continue

                    // With a known rate, sanity-check the derivation against the estimate. With an
                    // unknown currency there is nothing to compare to, so accept any positive drop.
                    if (SmsParser.knowsRate(cur.currency)) {
                        val estimate = cur.amount
                        if (estimate > 0.0 &&
                            (derived < estimate * MIN_RATIO || derived > estimate * MAX_RATIO)
                        ) continue
                    }
                    exact[cur.key] = derived
                }
            }

        return if (exact.isEmpty()) txns
        else txns.map { t ->
            // Flagged exact: this is the bank's own figure, so it can be compared against the
            // mid-market rate to show the markup.
            exact[t.key]?.let { t.copy(amount = it, amountExact = true) } ?: t
        }
    }
}
