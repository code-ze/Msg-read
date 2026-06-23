package com.example.smsspend.parser

import kotlin.math.abs

/**
 * Result of parsing a single SMS body. Pure data — no Android types — so it can be
 * unit-tested on the JVM. [merchantRaw] is the merchant exactly as it appeared in the
 * SMS; [TxnType] drives how the amount is treated in totals.
 */
data class ParsedTxn(
    val type: TxnType,
    val amount: Double,
    val merchantRaw: String,
    val date: Long,
    val key: String,
    val body: String
)

enum class TxnType {
    DEBIT,       // card / direct debit  -> spending
    WALLET_OUT,  // money sent           -> spending (transfer)
    WALLET_IN,   // money received       -> income
    DEPOSIT,     // generic deposit      -> income
    IPO,         // IPO subscription     -> investment
    DIVIDEND;    // dividend payout      -> income

    val isSpending: Boolean get() = this == DEBIT || this == WALLET_OUT
    val isIncome: Boolean get() = this == WALLET_IN || this == DEPOSIT || this == DIVIDEND
    val isInvestment: Boolean get() = this == IPO
}

/**
 * Parses Bank Muscat SMS messages.
 *
 * Documented message patterns (do NOT change a regex without adding a matching test —
 * a UI refactor must never silently break SMS reading):
 *
 *  1. Direct-debit card    "بطاقة الخصم المباشر ... <amt> OMR ... المباشر في <merchant> بتاريخ"
 *  2. Generic debit        "تم خصم OMR <amt> من حسابك ..."        (best-effort merchant)
 *  3. Wallet sent          "لقد قمت بإرسال ... OMR <amt> ... إلى <merchant> من حسابك"
 *  4. Wallet received      "لقد استلمت ... OMR <amt> ... من <merchant> في حسابك"
 *  5. Dividend (DIV)       "تم إيداع <amt> OMR ... DIV payment-<merchant> بتاريخ"
 *  6. Generic deposit      "تم إيداع <amt> OMR في حسابك ..."
 *  7. IPO subscription     "... debited for amount of OMR <amt> as subscription of IPO <merchant> for Investor Account ..."
 *
 * Ordering matters: more specific patterns (DIV before generic deposit, IPO before
 * generic debit wording) are checked first.
 */
object SmsParser {

    // amount written as "624.000 OMR"
    private val amountBeforeOmr = Regex("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*OMR")
    // amount written as "OMR 624.000"
    private val amountAfterOmr = Regex("OMR\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)")

    private val debitCardMerchant = Regex("المباشر في (.+?) بتاريخ")
    private val sentTo = Regex("إلى (.+?) من حسابك")
    private val recvFrom = Regex("من (.+?) في حسابك")
    private val divMerchant = Regex("DIV payment-?\\s*(.+?)\\s+بتاريخ")
    private val ipoMerchant = Regex("subscription of IPO\\s+(.+?)\\s+for Investor Account", RegexOption.IGNORE_CASE)
    private val genericDebitMerchant = Regex("في (.+?)(?:\\s+بتاريخ|\\s+على|\\.)")
    private val leadingCode = Regex("^[0-9]{4,}-?\\s*")

    // MCD (Muscat Clearing & Depository) — cash dividend per company
    private val mcdDivCompany = Regex("لشركة\\s*(.+)")
    // MCD — IPO subscription request registration
    private val ipoAppRef = Regex("طلب الإكتتاب رقم\\s*\"?([A-Za-z0-9\\-]+)\"?")
    // AGM invite (English)
    private val agmCompany = Regex("Investor\\s+\\w+,\\s*(.+?)\\s+invites you to attend", RegexOption.IGNORE_CASE)
    private val agmDate = Regex("On\\s+(\\d{1,2}/\\d{1,2}/\\d{4})", RegexOption.IGNORE_CASE)

    // Account balance carried in many bank SMS ("رصيدك الحالي هو <amt> OMR" / "balance is OMR <amt>").
    private val balanceArabic = Regex("رصيد[^0-9]{0,40}?([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*OMR")
    private val balanceEnglishAfter = Regex("balance[^0-9]{0,20}?OMR\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val balanceEnglishBefore = Regex("balance[^0-9]{0,20}?([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*OMR", RegexOption.IGNORE_CASE)

    // Bidirectional/format control marks that appear in bank/MCD SMS and break regexes.
    private val bidiMarks = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\u00AD]")

    /** Strips bidi/format control characters and normalizes spacing so regexes are stable. */
    fun normalize(s: String): String =
        bidiMarks.replace(s, "").replace(' ', ' ').replace(Regex("[\\s\\u00A0]+"), " ").trim()

    /** Quick filter so callers can skip clearly-irrelevant messages cheaply. */
    fun looksRelevant(body: String): Boolean =
        body.contains("OMR") ||
            body.contains("أرباح نقدية") ||
            body.contains("طلب الإكتتاب") ||
            body.contains("Annual General Meeting", ignoreCase = true)

    private fun num(s: String): Double = s.replace(",", "").toDoubleOrNull() ?: 0.0

    private fun firstAfterOmr(body: String): Double? =
        amountAfterOmr.find(body)?.groupValues?.get(1)?.let { num(it) }

    private fun firstBeforeOmr(body: String): Double? =
        amountBeforeOmr.find(body)?.groupValues?.get(1)?.let { num(it) }

    private fun stableKey(body: String, date: Long): String =
        abs((body + "|" + date).hashCode()).toString()

    fun parse(rawBody: String, date: Long): ParsedTxn? {
        val body = normalize(rawBody)
        if (!looksRelevant(body)) return null
        val key = stableKey(body, date)

        return when {
            // 7. IPO subscription (English)
            body.contains("subscription of IPO", ignoreCase = true) -> {
                val amt = firstAfterOmr(body) ?: firstBeforeOmr(body) ?: return null
                val m = ipoMerchant.find(body)?.groupValues?.get(1)?.trim()
                    ?: "IPO Subscription"
                ParsedTxn(TxnType.IPO, amt, m, date, key, body)
            }

            // 5a. MCD cash dividend per company ("أرباح نقدية ... لشركة <company>")
            body.contains("أرباح نقدية") -> {
                val amt = firstBeforeOmr(body) ?: firstAfterOmr(body) ?: return null
                val m = mcdDivCompany.find(body)?.groupValues?.get(1)?.trim() ?: "Dividend"
                ParsedTxn(TxnType.DIVIDEND, amt, m, date, key, body)
            }

            // 5b. Bank dividend — must be checked before the generic deposit branch
            body.contains("DIV payment", ignoreCase = true) ||
                (body.contains("تم إيداع") && body.contains("DIV", ignoreCase = false)) -> {
                val amt = firstBeforeOmr(body) ?: return null
                val m = divMerchant.find(body)?.groupValues?.get(1)?.trim() ?: "Dividend"
                ParsedTxn(TxnType.DIVIDEND, amt, m, date, key, body)
            }

            // 1. Direct-debit card
            body.contains("بطاقة الخصم المباشر") -> {
                val amt = firstBeforeOmr(body) ?: return null
                var m = debitCardMerchant.find(body)?.groupValues?.get(1)?.trim() ?: "Unknown"
                m = leadingCode.replace(m, "")
                ParsedTxn(TxnType.DEBIT, amt, m, date, key, body)
            }

            // 3. Wallet sent
            body.contains("لقد قمت بإرسال") -> {
                val amt = firstAfterOmr(body) ?: firstBeforeOmr(body) ?: return null
                val m = sentTo.find(body)?.groupValues?.get(1)?.trim() ?: "Transfer"
                ParsedTxn(TxnType.WALLET_OUT, amt, m, date, key, body)
            }

            // 4. Wallet received
            body.contains("لقد استلمت") -> {
                val amt = firstAfterOmr(body) ?: firstBeforeOmr(body) ?: return null
                val m = recvFrom.find(body)?.groupValues?.get(1)?.trim() ?: "Transfer"
                ParsedTxn(TxnType.WALLET_IN, amt, m, date, key, body)
            }

            // 6. Generic deposit
            body.contains("تم إيداع") -> {
                val amt = firstBeforeOmr(body) ?: return null
                ParsedTxn(TxnType.DEPOSIT, amt, "Deposit", date, key, body)
            }

            // 2. Generic debit (best-effort merchant)
            body.contains("تم خصم") -> {
                val amt = firstAfterOmr(body) ?: firstBeforeOmr(body) ?: return null
                var m = genericDebitMerchant.find(body)?.groupValues?.get(1)?.trim() ?: "Unknown"
                m = leadingCode.replace(m, "")
                if (m.isBlank()) m = "Unknown"
                ParsedTxn(TxnType.DEBIT, amt, m, date, key, body)
            }

            else -> null
        }
    }

    /** AGM (Annual General Meeting) invite — reveals a company you hold and its meeting date. */
    fun parseAgm(rawBody: String, date: Long): AgmInfo? {
        val body = normalize(rawBody)
        if (!body.contains("Annual General Meeting", ignoreCase = true)) return null
        val company = agmCompany.find(body)?.groupValues?.get(1)?.trim() ?: return null
        val meeting = agmDate.find(body)?.groupValues?.get(1)?.let { parseDmy(it) } ?: 0L
        return AgmInfo(company, meeting, date)
    }

    /**
     * Extracts the running account balance a transaction SMS reports ("رصيدك الحالي هو
     * <amt> OMR"). This lets the app track real balance over time for trends/predictions —
     * the user never has to type it. Returns null when the SMS carries no balance.
     */
    fun parseBalance(rawBody: String, date: Long): BalanceInfo? {
        val body = normalize(rawBody)
        if (!body.contains("OMR")) return null
        val m = balanceArabic.find(body)
            ?: balanceEnglishAfter.find(body)
            ?: balanceEnglishBefore.find(body)
            ?: return null
        val amt = num(m.groupValues[1])
        if (amt <= 0.0) return null
        return BalanceInfo(amt, date)
    }

    /** MCD IPO subscription-request confirmation — captures the application reference. */
    fun parseIpoApplication(rawBody: String, date: Long): IpoAppInfo? {
        val body = normalize(rawBody)
        if (!body.contains("طلب الإكتتاب")) return null
        val ref = ipoAppRef.find(body)?.groupValues?.get(1)?.trim() ?: return null
        return IpoAppInfo(ref, date)
    }

    /** Parses a dd/MM/yyyy date to epoch millis (local), or 0 on failure. */
    private fun parseDmy(s: String): Long = try {
        val p = s.split("/")
        java.util.Calendar.getInstance().apply {
            clear(); set(p[2].toInt(), p[1].toInt() - 1, p[0].toInt(), 12, 0, 0)
        }.timeInMillis
    } catch (e: Exception) { 0L }
}

/** The running account balance an SMS reported, at the time it was sent. */
data class BalanceInfo(val balance: Double, val date: Long)

/** An Annual General Meeting invite extracted from an SMS. */
data class AgmInfo(val company: String, val meetingDate: Long, val smsDate: Long)

/** An IPO subscription-request confirmation (MCD). */
data class IpoAppInfo(val reference: String, val date: Long)
