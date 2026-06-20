package com.example.smsspend.parser

/**
 * Merges the many ways one company shows up across bank/MCD SMS — English, Arabic, codes,
 * truncations — into a single canonical name. Without this, dividends from "OQ EXPLORATIO"
 * (bank DIV) and "اوكيو للاستكشاف" (MCD) appear as two different companies.
 *
 * Pure Kotlin and unit-tested. Matching is case-insensitive and tolerant of truncation
 * (the bank truncates long names), bidi marks and spacing.
 */
object CompanyNames {

    // canonical display name -> recognizable variants (any language / spelling / code)
    private val aliases: List<Pair<String, List<String>>> = listOf(
        "OQ Exploration & Production" to listOf("OQ EXPLORATIO", "OQ EXPLORATION", "OQEP", "اوكيو للاستكشاف", "اوكيو للاستكشاف والانتاج"),
        "OQ Gas Networks" to listOf("OQ GAS", "OQGN", "اوكيو لشبكات الغاز"),
        "OQ Base Industries" to listOf("OQ BASE", "OQBI", "اوكيو للصناعات الاساسية"),
        "Abraj Energy Services" to listOf("ABRAJ", "ابراج للطاقة", "ابراج"),
        "Bank Muscat" to listOf("BANK MUSCAT", "BKMB", "بنك مسقط"),
        "Bank Dhofar" to listOf("BANK DHOFAR", "BKDB", "بنك ظفار"),
        "Sohar International" to listOf("SOHAR INTERNATIONAL", "SOHAR INTL", "BKSB", "صحار الدولي"),
        "OmanTel" to listOf("OMAN TEL", "OMANTEL", "OTL", "عمانتل"),
        "Ooredoo" to listOf("OOREDOO", "ORDS", "اوريدو"),
        "Oman Cables" to listOf("OMAN CABLES", "OCAI", "كابلات عمان"),
        "Galfar Engineering" to listOf("GALFAR", "GECS", "جلفار"),
        "Renaissance Services" to listOf("RENAISSANCE", "RNSS", "النهضة"),
        "Al Anwar" to listOf("AL ANWAR", "الانوار"),
        "Oman Flour Mills" to listOf("FLOUR MILLS", "OFMI", "مطاحن"),
        "Sembcorp Salalah" to listOf("SEMBCORP", "SCSP", "صلالة"),
        "Phoenix Power" to listOf("PHOENIX POWER", "PHPC", "فينيكس"),
        "Sohar Power" to listOf("SOHAR POWER", "SPCS"),
        "Oman Telecommunications" to listOf("OMANTEL", "عمانتل")
    )

    // Pre-normalized for fast matching.
    private val normalized: List<Pair<String, List<String>>> =
        aliases.map { (canon, variants) -> canon to variants.map { norm(it) } }

    private val bidi = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\u00AD]")

    private fun norm(s: String): String =
        bidi.replace(s, "").replace(Regex("[\\s\\u00A0]+"), " ").trim().uppercase()

    /** Returns the canonical company name for [name], or [name] unchanged if unrecognized. */
    fun canonical(name: String): String {
        val n = norm(name)
        if (n.isEmpty()) return name
        for ((canon, variants) in normalized) {
            if (variants.any { v -> v.isNotEmpty() && (n.contains(v) || v.contains(n)) }) return canon
        }
        return name
    }
}
