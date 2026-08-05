package com.example.smsspend.parser

/**
 * Cleans up raw merchant strings and assigns categories.
 *
 * Pure Kotlin so it can be unit-tested. Learned per-merchant rules live in the database
 * and are applied by the repository AFTER this default categorization, so that fixing one
 * "TALABAT" reclassifies every TALABAT.
 */
object Categorizer {

    const val INVESTMENTS = "Investments"
    const val DIVIDENDS = "Dividends"
    const val INCOME = "Income"
    const val TRANSFERS = "Transfers"
    const val OTHER = "Other"

    /** Categories that represent money coming in (kept out of spending totals). */
    val incomeCategories = setOf(INCOME, DIVIDENDS)

    /** Full ordered list shown in the re-categorize picker (built-in, top-level). */
    val allCategories = listOf(
        "Food Delivery", "Cafes & Tea", "Restaurants", "Groceries",
        "Fuel & Transport", "Pharmacy & Health", "Telecom", "Utilities",
        "Rent", "Subscriptions", "Online Shopping", "Charity", TRANSFERS,
        INVESTMENTS, DIVIDENDS, INCOME, OTHER
    )

    /**
     * Built-in sub-categories, seeded so common splits exist out of the box. Users can add
     * their own to any category. Order is the display order.
     */
    val builtInSubcategories: Map<String, List<String>> = linkedMapOf(
        "Utilities" to listOf("Electricity", "Water", "Gas"),
        "Fuel & Transport" to listOf("Fuel", "Taxi", "Parking"),
        "Telecom" to listOf("Mobile", "Internet"),
        "Groceries" to listOf("Supermarket", "Bakery"),
        "Restaurants" to listOf("Fast Food", "Dine-in")
    )

    /** Keyword → sub-category, scoped per parent category, for automatic sub-tagging. */
    private val subKeywordRules: Map<String, List<Pair<String, List<String>>>> = mapOf(
        "Utilities" to listOf(
            "Electricity" to listOf("NAMA", "MAJAN", "MAZOON", "MUSCAT ELECT", "ELECTRIC", "RURAL AREAS", "TANWEER"),
            "Water" to listOf("DIAM", "WATER", "HAYA", "MIYAH"),
            "Gas" to listOf("GAS", "GHAZ")
        ),
        "Fuel & Transport" to listOf(
            "Fuel" to listOf("OMAN OIL", "SHELL", "PETROL", "FUEL", "AL MAHA"),
            "Taxi" to listOf("OTAXI", "OOREDOO TAXI", "TAXI", "MWASALAT"),
            "Parking" to listOf("PARKING", "MAWQIF")
        ),
        "Telecom" to listOf(
            "Mobile" to listOf("RECHARGE", "PREPAID", "MOBILE"),
            "Internet" to listOf("INTERNET", "FIBER", "BROADBAND", "WIFI")
        )
    )

    private val keywordRules: List<Pair<String, List<String>>> = listOf(
        "Food Delivery" to listOf("TALABAT", "JAHEZ", "AKEED"),
        "Cafes & Tea" to listOf("TEA TIME", "AROMA", "FUN JUICE", "KYAN BARK", "EFENDI", "CAFE", "COFFEE"),
        "Restaurants" to listOf("RESTURANT", "RESTAURANT", "TEXAS CHICKEN", "SHAWARMA", "HEQBAH", "CHOCOLALA", "KUCU", "GRILL", "PIZZA", "BURGER"),
        "Groceries" to listOf("LULU", "RAHAL", "FRESH EASY", "ABU TURKEY", "BASAMAT", "EXPRESS SHOPPING", "ATLAS", "RABI", "GHORBAN", "BRIGHT GULF", "AHLAIN", "MARKET", "HYPER", "SUPERMARKET"),
        "Fuel & Transport" to listOf("OMAN OIL", "SHELL", "PRECISION TUNE", "AL MAHA", "1718", "PETROL", "FUEL", "OOREDOO TAXI", "OTAXI"),
        "Pharmacy & Health" to listOf("PHARMACY", "HOSPITAL", "CLINIC", "MEDICAL", "DENTAL"),
        "Telecom" to listOf("OMANTEL", "OOREDOO"),
        "Utilities" to listOf("NAMA", "ELECTRICITY", "WATER", "DIAM"),
        "Subscriptions" to listOf("ANTHROPIC", "CLAUDE", "NETFLIX", "SPOTIFY", "GOOGLE", "APPLE.COM", "YOUTUBE", "OPENAI"),
        "Online Shopping" to listOf("ALIEXPRESS", "AMAZON", "NOON", "SHEIN", "TEMU"),
        "Charity" to listOf("SADAQAT", "DAR AL ATTA", "M.O.A", "CHARITY")
    )

    private val trailingNoise = Regex("\\s+(OM|OMN|MUSCAT|MUS|SOHAR|SALALAH|SEEB|MCT)\\b\\.?$", RegexOption.IGNORE_CASE)
    private val multiSpace = Regex("\\s+")
    private val leadingCode = Regex("^[0-9]{4,}-?\\s*")

    /**
     * Normalizes a raw merchant string into a stable, human-friendly display name.
     * Used both for display and as the key for learned rules, so cleanup must be
     * deterministic.
     */
    fun cleanMerchant(raw: String): String {
        var m = raw.trim()
        m = leadingCode.replace(m, "")
        m = m.replace(multiSpace, " ").trim()
        // strip a trailing standalone branch number, e.g. "TALABAT 12345"
        m = m.replace(Regex("\\s+[0-9]{3,}$"), "")
        m = trailingNoise.replace(m, "").trim()
        if (m.isBlank()) return raw.trim()
        return titleCase(m)
    }

    private fun titleCase(s: String): String =
        s.split(" ").joinToString(" ") { word ->
            when {
                word.length <= 1 -> word.uppercase()
                // keep all-caps acronyms in parentheses as-is e.g. (OMIF)
                word.startsWith("(") -> word
                else -> word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }

    /** Default category from the parsed type + merchant keywords. */
    fun defaultCategory(type: TxnType, merchantClean: String): String {
        when (type) {
            TxnType.IPO -> return INVESTMENTS
            TxnType.DIVIDEND -> return DIVIDENDS
            TxnType.WALLET_IN, TxnType.DEPOSIT -> return INCOME
            TxnType.WALLET_OUT -> {
                val kw = keywordMatch(merchantClean)
                return kw ?: TRANSFERS
            }
            TxnType.DEBIT -> {
                val kw = keywordMatch(merchantClean)
                return kw ?: OTHER
            }
        }
    }

    private fun keywordMatch(merchant: String): String? {
        val m = merchant.uppercase()
        for ((cat, keys) in keywordRules) if (keys.any { m.contains(it) }) return cat
        return null
    }

    /** Best-effort default sub-category from the merchant within a known category ("" = none). */
    fun defaultSubcategory(category: String, merchantClean: String): String {
        val rules = subKeywordRules[category] ?: return ""
        val m = merchantClean.uppercase()
        for ((sub, keys) in rules) if (keys.any { m.contains(it) }) return sub
        return ""
    }
}
