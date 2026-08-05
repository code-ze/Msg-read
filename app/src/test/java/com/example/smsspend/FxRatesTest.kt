package com.example.smsspend

import com.example.smsspend.data.FxRateSource
import com.example.smsspend.model.FxRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FxRatesTest {

    /** A representative EUR/USD; the exact value only matters for the euro-linked cases. */
    private val usdPerEur = 1.08

    private fun live(map: Map<String, Double>): (String) -> Double? = { map[it] }

    // ---- direction ----

    /**
     * The whole feature hinges on comparing rates in the same direction. A BAM/OMR rate of ~4.71
     * and an OMR/BAM rate of ~0.212 describe the same thing; subtracting one from the other
     * yields nonsense, so everything is normalised to OMR per unit.
     */
    @Test fun bamIsPricedAsOmrPerUnitNotItsReciprocal() {
        val r = FxRates.trueOmrPerUnit("BAM", usdPerEur)!!
        // 1 OMR buys 2.6008 / 1.08 * 1.95583 = 4.7099 BAM, so one BAM costs ~0.2123 OMR.
        assertEquals(0.21232, r.omrPerUnit, 0.0001)
        assertTrue("must be the small number, not 4.71", r.omrPerUnit < 1.0)
    }

    @Test fun bamMarkupMatchesTheWorkedExample() {
        val r = FxRates.trueOmrPerUnit("BAM", usdPerEur)!!
        // Spend 100 BAM, bank takes 21.75 OMR.
        val markup = FxRates.markupPercent(21.75, 100.0, r.omrPerUnit)!!
        assertEquals(2.44, markup, 0.02)
    }

    // ---- the rial's peg means USD and Gulf currencies need no network ----

    @Test fun usdNeedsNoLiveRate() {
        val r = FxRates.trueOmrPerUnit("USD")
        assertNotNull(r); r!!
        assertEquals(FxRates.Basis.PEG, r.basis)
        assertEquals(1.0 / 2.6008, r.omrPerUnit, 0.000001)
    }

    @Test fun gulfCurrenciesResolveOffline() {
        for (code in listOf("AED", "SAR", "QAR", "BHD")) {
            val r = FxRates.trueOmrPerUnit(code)
            assertNotNull("$code should price with no network", r)
            assertEquals(FxRates.Basis.PEG, r!!.basis)
            assertTrue(r.omrPerUnit > 0.0)
        }
        // A dirham is worth about a tenth of a rial.
        assertEquals(0.1047, FxRates.trueOmrPerUnit("AED")!!.omrPerUnit, 0.001)
    }

    /** The real Cars on Booking charge: USD 790.420 billed at 311.950 OMR. */
    @Test fun realUsdChargeShowsTheBanksMarkup() {
        val r = FxRates.trueOmrPerUnit("USD")!!
        val markup = FxRates.markupPercent(311.950, 790.420, r.omrPerUnit)!!
        assertEquals(2.64, markup, 0.05)
    }

    // ---- euro and floating ----

    @Test fun eurUsesTheLiveRate() {
        val r = FxRates.trueOmrPerUnit("EUR", usdPerEur)!!
        assertEquals(FxRates.Basis.LIVE, r.basis)
        assertEquals(1.08 / 2.6008, r.omrPerUnit, 0.000001)
    }

    @Test fun floatingCurrencyUsesTheLiveTable() {
        // 0.79 GBP per USD -> 1 GBP = 1.2658 USD.
        val r = FxRates.trueOmrPerUnit("GBP", usdPerEur, live(mapOf("GBP" to 0.79)))!!
        assertEquals((1.0 / 0.79) / 2.6008, r.omrPerUnit, 0.000001)
    }

    @Test fun unknownCurrencyReturnsNullRatherThanAGuess() {
        assertNull(FxRates.trueOmrPerUnit("KWD", usdPerEur, live(emptyMap())))
    }

    @Test fun euroPeggedCurrencyNeedsTheEuroRate() {
        assertNull("no EUR/USD means BAM can't be priced", FxRates.trueOmrPerUnit("BAM", null))
    }

    // ---- markup guards ----

    @Test fun markupIsZeroAtTheMidMarketRate() {
        val r = FxRates.trueOmrPerUnit("USD")!!
        val exact = 100.0 * r.omrPerUnit
        assertEquals(0.0, FxRates.markupPercent(exact, 100.0, r.omrPerUnit)!!, 0.0001)
    }

    @Test fun markupCanBeNegativeWhenTheBankBeatsMidMarket() {
        val r = FxRates.trueOmrPerUnit("USD")!!
        val cheap = 100.0 * r.omrPerUnit * 0.99
        assertTrue(FxRates.markupPercent(cheap, 100.0, r.omrPerUnit)!! < 0.0)
    }

    @Test fun markupRejectsNonsenseInputs() {
        assertNull(FxRates.markupPercent(0.0, 100.0, 0.38))
        assertNull(FxRates.markupPercent(10.0, 0.0, 0.38))
        assertNull(FxRates.markupPercent(10.0, 100.0, 0.0))
    }

    @Test fun onlyNonPeggedCurrenciesNeedALiveRate() {
        assertTrue(FxRates.needsLiveRate("EUR"))
        assertTrue(FxRates.needsLiveRate("BAM"))
        assertTrue(FxRates.needsLiveRate("GBP"))
        assertTrue(!FxRates.needsLiveRate("USD"))
        assertTrue(!FxRates.needsLiveRate("AED"))
    }

    // ---- provider parsing ----

    @Test fun parsesFrankfurterResponse() {
        val json = """{"amount":1.0,"base":"USD","date":"2026-07-29","rates":{"EUR":0.9259,"GBP":0.79}}"""
        val rates = FxRateSource.parse(json)!!
        assertEquals(0.9259, rates["EUR"]!!, 0.0001)
        assertEquals(0.79, rates["GBP"]!!, 0.0001)
    }

    @Test fun parsesErApiResponse() {
        val json = """{"result":"success","base_code":"USD","rates":{"EUR":0.9259,"AED":3.6725}}"""
        val rates = FxRateSource.parse(json)!!
        assertEquals(3.6725, rates["AED"]!!, 0.0001)
    }

    @Test fun rejectsAResponseInADifferentBase() {
        val json = """{"base":"EUR","rates":{"USD":1.08}}"""
        assertNull("a non-USD base would silently invert every rate", FxRateSource.parse(json))
    }

    @Test fun rejectsRubbish() {
        assertNull(FxRateSource.parse("not json"))
        assertNull(FxRateSource.parse("""{"rates":{}}"""))
    }
}
