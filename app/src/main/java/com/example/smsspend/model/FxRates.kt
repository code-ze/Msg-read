package com.example.smsspend.model

/**
 * Works out what a foreign purchase *should* have cost in rials, so the bank's cross-currency
 * markup becomes visible.
 *
 * Everything is normalised to one direction — **OMR per 1 unit of the foreign currency** — because
 * mixing directions is the easy way to get a nonsense answer. If X/OMR is 4.71 and OMR/X is 0.212,
 * subtracting one from the other is meaningless; they have to be compared in the same direction.
 *
 * The chain is always: foreign currency → USD → OMR.
 *
 *  - The rial is hard-pegged: 1 OMR = [USD_PER_OMR] USD, and has been for decades. So the last leg
 *    never needs a network call.
 *  - Several currencies are themselves hard-pegged (the Gulf ones to USD, a handful to the euro).
 *    For those the whole chain is fixed and the markup can be computed with no network at all.
 *  - Everything else needs a live USD rate.
 *
 * Pure Kotlin, no Android types, so the arithmetic is unit-tested.
 */
object FxRates {

    /** The rial's peg. 1 OMR = 2.6008 USD. */
    const val USD_PER_OMR = 2.6008

    /** The euro pegs quoted as "units of X per 1 EUR". */
    private const val BAM_PER_EUR = 1.95583

    /** Currencies pegged to the dollar, quoted as "units of X per 1 USD". */
    private val peggedToUsd = mapOf(
        "USD" to 1.0,       // the anchor itself — the rial's peg prices it with no live rate
        "AED" to 3.6725,    // UAE dirham
        "SAR" to 3.7500,    // Saudi riyal
        "QAR" to 3.6400,    // Qatari riyal
        "BHD" to 0.3760,    // Bahraini dinar
        "JOD" to 0.7090,    // Jordanian dinar
        "OMR" to 1.0 / USD_PER_OMR
    )

    /** Currencies pegged to the euro, quoted as "units of X per 1 EUR". */
    private val peggedToEur = mapOf(
        "BAM" to BAM_PER_EUR,   // Bosnian mark
        "BGN" to 1.95583,       // Bulgarian lev
        "DKK" to 7.46038,       // Danish krone
        "XOF" to 655.957,       // West African CFA franc
        "XAF" to 655.957        // Central African CFA franc
    )

    /**
     * How the OMR value of one unit was established — worth surfacing, because a rate that came
     * from a fixed peg is exact while a live one is only as fresh as the last fetch.
     */
    enum class Basis { PEG, LIVE }

    data class TrueRate(val omrPerUnit: Double, val basis: Basis)

    /**
     * The mid-market cost in rials of one unit of [currency].
     *
     * [usdPerEur] and [liveUsdPerUnit] are only consulted for currencies that actually float or
     * are euro-pegged; a dollar-pegged currency resolves with no live data whatsoever. Returns
     * null when there is no honest way to price it, rather than guessing.
     */
    fun trueOmrPerUnit(
        currency: String,
        usdPerEur: Double? = null,
        liveUsdPerUnit: ((String) -> Double?)? = null
    ): TrueRate? {
        val code = currency.uppercase()

        peggedToUsd[code]?.let { perUsd ->
            if (perUsd <= 0.0) return null
            // 1 unit = (1/perUsd) USD, and 1 USD = 1/USD_PER_OMR OMR.
            return TrueRate((1.0 / perUsd) / USD_PER_OMR, Basis.PEG)
        }

        peggedToEur[code]?.let { perEur ->
            if (perEur <= 0.0 || usdPerEur == null || usdPerEur <= 0.0) return null
            // 1 unit = (1/perEur) EUR = (usdPerEur/perEur) USD.
            return TrueRate((usdPerEur / perEur) / USD_PER_OMR, Basis.LIVE)
        }

        if (code == "EUR") {
            if (usdPerEur == null || usdPerEur <= 0.0) return null
            return TrueRate(usdPerEur / USD_PER_OMR, Basis.LIVE)
        }

        val usd = liveUsdPerUnit?.invoke(code) ?: return null
        if (usd <= 0.0) return null
        return TrueRate(usd / USD_PER_OMR, Basis.LIVE)
    }

    /**
     * What the bank added on top of the mid-market rate, as a percentage.
     *
     * [omrCharged] must be the figure the bank actually took, not an estimate the app produced —
     * comparing our own converted guess against the true rate would just measure our guess.
     * Positive means the purchase cost more rials than the mid-market rate implies.
     */
    fun markupPercent(
        omrCharged: Double,
        foreignAmount: Double,
        trueOmrPerUnit: Double
    ): Double? {
        if (omrCharged <= 0.0 || foreignAmount <= 0.0 || trueOmrPerUnit <= 0.0) return null
        val effective = omrCharged / foreignAmount          // OMR per unit, as charged
        return (effective - trueOmrPerUnit) / trueOmrPerUnit * 100.0
    }

    /** Every currency that can be priced without a live rate. */
    fun offlineCurrencies(): Set<String> = peggedToUsd.keys

    /** True when [currency] needs a live USD/EUR rate to be priced at all. */
    fun needsLiveRate(currency: String): Boolean =
        !peggedToUsd.containsKey(currency.uppercase())
}
