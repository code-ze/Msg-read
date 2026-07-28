package com.example.smsspend

import com.example.smsspend.model.CardCharges
import com.example.smsspend.parser.CardPaymentInfo
import com.example.smsspend.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercised with the real Bank Muscat card sequence, where the true rial cost of a USD purchase
 * is only recoverable from the drop in available limit.
 */
class CardChargesTest {

    private fun parse(body: String, date: Long) =
        SmsParser.parse(body, date) ?: error("should parse: $body")

    private val google = "Card 420460******0444 used for USD 1.000 at GOOGLE*ANDROID TEMP  on 27/07/2026 19:38:54. Available limit OMR 951.806."
    private val tmdone = "Card 420460******0444 used for OMR 3.800 at Paymob-*TMDONE  on 27/07/2026 19:39:47. Available limit OMR 948.006."
    private val booking = "Card 420460******0444 used for USD 790.420 at Cars on Booking  on 27/07/2026 20:04:03. Available limit OMR 636.448."
    // The bank re-sent the same charge 10 minutes later; the limit is unchanged.
    private val bookingResend = "Card 420460******0444 used for USD 790.420 at Cars on Booking  on 27/07/2026 20:14:02. Available limit OMR 636.448."

    @Test fun derivesExactOmrForForeignChargeFromLimitDrop() {
        val txns = listOf(parse(google, 1), parse(tmdone, 2), parse(booking, 3))
        val out = CardCharges.process(txns, emptyList())

        val b = out.first { it.merchantRaw == "Cars on Booking" }
        // 948.006 (limit before) − 636.448 (limit after) = 311.558 actually charged.
        assertEquals(311.558, b.amount, 0.001)
        // The original USD figure is preserved for display.
        assertEquals(790.420, b.originalAmount, 0.001)
        assertEquals("USD", b.currency)
    }

    @Test fun domesticChargesAreLeftAlone() {
        val txns = listOf(parse(google, 1), parse(tmdone, 2), parse(booking, 3))
        val out = CardCharges.process(txns, emptyList())
        assertEquals(3.800, out.first { it.merchantRaw == "Paymob-*TMDONE" }.amount, 0.0001)
    }

    @Test fun dropsResentDuplicateWithUnchangedLimit() {
        val txns = listOf(parse(google, 1), parse(tmdone, 2), parse(booking, 3), parse(bookingResend, 4))
        val out = CardCharges.process(txns, emptyList())

        val bookings = out.filter { it.merchantRaw == "Cars on Booking" }
        assertEquals("the re-sent notification must not be counted twice", 1, bookings.size)
    }

    @Test fun keepsGenuineRepeatChargeThatMovedTheLimit() {
        val second = "Card 420460******0444 used for OMR 3.800 at Paymob-*TMDONE  on 27/07/2026 21:00:00. Available limit OMR 944.206."
        val txns = listOf(parse(tmdone, 2), parse(second, 5))
        val out = CardCharges.process(txns, emptyList())
        assertEquals("a real second charge lowers the limit again", 2, out.size)
    }

    @Test fun addsBackPaymentsMadeBetweenTwoCharges() {
        // A 400 payment lands between the two spends, pushing the limit up.
        val before = "Card 420460******0444 used for OMR 10.000 at SHOP A  on 28/07/2026 09:00:00. Available limit OMR 500.000."
        val after = "Card 420460******0444 used for USD 100.000 at SHOP B  on 28/07/2026 11:00:00. Available limit OMR 860.000."
        val payment = CardPaymentInfo("0444", 400.0, dateOf(2026, 7, 28, 10, 0, 0))

        val txns = listOf(parse(before, 10), parse(after, 11))
        val out = CardCharges.process(txns, listOf(payment))

        // 500 + 400 − 860 = 40 OMR actually charged for the USD 100 purchase.
        assertEquals(40.0, out.first { it.merchantRaw == "SHOP B" }.amount, 0.001)
    }

    @Test fun fallsBackToRateTableWhenChainIsBroken() {
        // Limit went UP with no recorded payment (a missed SMS), so the delta is meaningless.
        val a = "Card 420460******0444 used for OMR 10.000 at SHOP A  on 28/07/2026 09:00:00. Available limit OMR 500.000."
        val b = "Card 420460******0444 used for USD 100.000 at SHOP B  on 28/07/2026 11:00:00. Available limit OMR 900.000."
        val out = CardCharges.process(listOf(parse(a, 10), parse(b, 11)), emptyList())

        val shopB = out.first { it.merchantRaw == "SHOP B" }
        // Estimated via the peg (~0.394), not the nonsensical negative delta.
        assertTrue("should keep the FX estimate", shopB.amount in 35.0..45.0)
    }

    @Test fun ignoresWildlyImplausibleDelta() {
        // Limit dropped far more than the purchase could explain (another charge's SMS missing).
        val a = "Card 420460******0444 used for OMR 10.000 at SHOP A  on 28/07/2026 09:00:00. Available limit OMR 900.000."
        val b = "Card 420460******0444 used for USD 10.000 at SHOP B  on 28/07/2026 11:00:00. Available limit OMR 100.000."
        val out = CardCharges.process(listOf(parse(a, 10), parse(b, 11)), emptyList())

        val shopB = out.first { it.merchantRaw == "SHOP B" }
        assertTrue("800 OMR for a USD 10 buy must be rejected", shopB.amount < 20.0)
    }

    private fun dateOf(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Long =
        java.util.Calendar.getInstance().apply {
            clear(); set(y, mo - 1, d, h, mi, s)
        }.timeInMillis
}
