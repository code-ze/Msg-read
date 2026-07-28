package com.example.smsspend.parser

import kotlin.math.abs

/**
 * Result of parsing a single SMS body. Pure data — no Android types — so it can be
 * unit-tested on the JVM. [merchantRaw] is the merchant exactly as it appeared in the
 * SMS; [TxnType] drives how the amount is treated in totals.
 */
data class ParsedTxn(
    val type: TxnType,
    /** Always OMR — foreign amounts are converted so every total in the app is one currency. */
    val amount: Double,
    val merchantRaw: String,
    val date: Long,
    val key: String,
    val body: String,
    /** Currency the transaction was billed in ("OMR" for domestic). */
    val currency: String = "OMR",
    /** Amount in [currency]; 0 when the transaction was already in OMR. */
    val originalAmount: Double = 0.0,
    /** Last 4 digits of the credit card, when this came from a card SMS. */
    val cardLast4: String = "",
    /** Remaining credit (OMR) the card SMS reported, 0 when absent. */
    val availableLimit: Double = 0.0
) {
    /** True when this was billed in a foreign currency and [amount] is a conversion. */
    val isForeign: Boolean get() = currency != "OMR" && originalAmount > 0.0
    val isCard: Boolean get() = cardLast4.isNotEmpty()
}

/** A payment that landed on a credit card, reducing what's owed. Not spending. */
data class CardPaymentInfo(val cardLast4: String, val amount: Double, val date: Long)

/** The remaining credit a card SMS reported, at the time it was sent. */
data class CardLimitInfo(val cardLast4: String, val availableLimit: Double, val date: Long)

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
 *  Arabic account messages:
 *  1. Direct-debit card    "بطاقة الخصم المباشر ... <amt> OMR ... المباشر في <merchant> بتاريخ"
 *  2. Generic debit        "تم خصم OMR <amt> من حسابك ..."        (best-effort merchant)
 *  3. Wallet sent          "لقد قمت بإرسال ... OMR <amt> ... إلى <merchant> من حسابك"
 *  4. Wallet received      "لقد استلمت ... OMR <amt> ... من <merchant> في حسابك"
 *  5. Dividend (DIV)       "تم إيداع <amt> OMR ... DIV payment-<merchant> بتاريخ"
 *  6. Generic deposit      "تم إيداع <amt> OMR في حسابك ..."
 *  7. IPO subscription     "... debited for amount of OMR <amt> as subscription of IPO <merchant> for Investor Account ..."
 *
 *  English credit card messages:
 *  8. CC spend (OMR)       "Card XXXX used for OMR <amt> at <merchant> on DD/MM/YYYY HH:MM:SS. Available limit OMR <limit>."
 *  9. CC spend (foreign)   "Card XXXX used for USD <amt> at <merchant> on DD/MM/YYYY HH:MM:SS. Available limit OMR <limit>."
 *
 *  Ignored:
 *  - "Payment of OMR X has been credited on your card XXXX"  (CC receiving payment — skip)
 *  - "Your a/c has been debited for OMR X as payment towards bank muscat Credit Card" (skip — CC spending already tracked per-transaction)
 *  - "... was reversed"  (reversal — skipped; both the original and reversal cancel out)
 *  - OTP messages
 *
 * Ordering matters: more specific patterns (DIV before generic deposit, IPO before
 * generic debit wording) are checked first.
 */
object SmsParser {

    // amount written as "624.000 OMR"
    private val amountBeforeOmr = Regex("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*OMR")
    // amount written as "OMR 624.000"
    private val amountAfterOmr = Regex("OMR\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)")

    // English credit card spend: "Card XXXX used for OMR/USD 139.500 at MERCHANT  on 28/07/2026 12:33:39. Available limit OMR 928.448."
    // The merchant group is non-greedy but anchored by the date, so names containing " on "
    // (e.g. "Cars on Booking") are captured whole rather than truncated at the first " on ".
    private val ccSpend = Regex(
        """Card\s+([\d*]+)\s+used\s+for\s+([A-Z]{3})\s+([0-9][0-9,]*(?:\.[0-9]+)?)\s+at\s+(.+?)\s+on\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2})""",
        RegexOption.IGNORE_CASE
    )
    private val ccAvailableLimit = Regex(
        """Available\s+limit\s+OMR\s*([0-9][0-9,]*(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE
    )
    // "Payment of OMR 200.000 has been credited on your card 420460******0444 on 28/07/2026 13:39:57"
    private val ccPayment = Regex(
        """Payment\s+of\s+OMR\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s+has\s+been\s+credited\s+on\s+your\s+card\s+([\d*]+)""",
        RegexOption.IGNORE_CASE
    )

    // English mobile payment: "You have sent OMR 4.000 to NAME from your a/c 0311... on 28/07/2026 14:14:49"
    private val mobileSent = Regex(
        """You\s+have\s+sent\s+OMR\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s+to\s+(.+?)\s+from\s+your\s+a/c""",
        RegexOption.IGNORE_CASE
    )
    private val mobileReceived = Regex(
        """You\s+have\s+received\s+OMR\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s+from\s+(.+?)\s+(?:to|in)\s+your\s+a/c""",
        RegexOption.IGNORE_CASE
    )
    private val englishDateTime = Regex("""on\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)

    /**
     * Approximate OMR value of one unit of each foreign currency, used only as a *fallback*.
     *
     * The rial is pegged (1 OMR = 2.6008 USD), and the bank adds roughly a 2.5% cross-currency
     * markup, so USD lands near 0.394. These are estimates: the exact charge is normally
     * recovered from the drop in "Available limit" between two card SMS (see CardCharges),
     * which reflects what the bank actually billed including fees.
     */
    private val fxToOmr = mapOf(
        "OMR" to 1.0,
        "USD" to 0.3942,
        "AED" to 0.1073,
        "SAR" to 0.1051,
        "QAR" to 0.1083,
        "BHD" to 1.0460,
        "KWD" to 1.2800,
        "EUR" to 0.4280,
        "GBP" to 0.5000,
        "INR" to 0.0046
    )

    /** Converts [amount] in [currency] to OMR using the fallback rate table. */
    fun toOmr(amount: Double, currency: String): Double =
        amount * (fxToOmr[currency.uppercase()] ?: 1.0)

    /** True when a rate is known for [currency] (so a conversion is meaningful). */
    fun knowsRate(currency: String): Boolean = fxToOmr.containsKey(currency.uppercase())

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
    // Mobile-payment SMS report the account balance as "Avl Bal OMR 10264.641."
    private val avlBal = Regex("Avl\\s*Bal(?:ance)?\\s*OMR\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)

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
            body.contains("Annual General Meeting", ignoreCase = true) ||
            body.contains("used for", ignoreCase = true)

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
            // Skip: CC payment credited to card (not spending, just account transfer)
            body.contains("has been credited on your card", ignoreCase = true) -> null

            // Skip: account debited as CC payment (CC spending already tracked per-item)
            body.contains("as payment towards bank muscat Credit Card", ignoreCase = true) -> null

            // Skip: reversed transactions (both legs cancel; original usually already recorded)
            body.contains("was reversed", ignoreCase = true) -> null

            // Skip: OTP messages (duplicate of the matching CC spend SMS)
            body.contains("One Time Password", ignoreCase = true) -> null

            // 8/9. English CC spend: "Card XXXX used for OMR/USD X.XXX at MERCHANT on DATE"
            body.contains("used for", ignoreCase = true) &&
                body.contains("Available limit", ignoreCase = true) -> {
                val m = ccSpend.find(body) ?: return null
                val last4 = m.groupValues[1].takeLast(4)
                val currency = m.groupValues[2].uppercase()
                val billed = num(m.groupValues[3])
                if (billed <= 0) return null
                val merchant = m.groupValues[4].trim()
                val txnDate = parseDmyHms(m.groupValues[5])
                val limit = ccAvailableLimit.find(body)?.groupValues?.get(1)?.let { num(it) } ?: 0.0
                ParsedTxn(
                    type = TxnType.DEBIT,
                    // Foreign amounts are converted so "Spent" is always OMR. CardCharges later
                    // replaces this estimate with the exact figure from the limit drop.
                    amount = if (currency == "OMR") billed else toOmr(billed, currency),
                    merchantRaw = merchant,
                    date = if (txnDate > 0) txnDate else date,
                    key = key,
                    body = body,
                    currency = currency,
                    originalAmount = if (currency == "OMR") 0.0 else billed,
                    cardLast4 = last4,
                    availableLimit = limit
                )
            }

            // 10. English mobile payment sent
            body.contains("You have sent", ignoreCase = true) -> {
                val m = mobileSent.find(body) ?: return null
                val amt = num(m.groupValues[1])
                if (amt <= 0) return null
                val txnDate = englishDateTime.find(body)?.groupValues?.get(1)?.let { parseDmyHms(it) } ?: 0L
                ParsedTxn(
                    TxnType.WALLET_OUT, amt, m.groupValues[2].trim(),
                    if (txnDate > 0) txnDate else date, key, body
                )
            }

            // 11. English mobile payment received
            body.contains("You have received", ignoreCase = true) -> {
                val m = mobileReceived.find(body) ?: return null
                val amt = num(m.groupValues[1])
                if (amt <= 0) return null
                val txnDate = englishDateTime.find(body)?.groupValues?.get(1)?.let { parseDmyHms(it) } ?: 0L
                ParsedTxn(
                    TxnType.WALLET_IN, amt, m.groupValues[2].trim(),
                    if (txnDate > 0) txnDate else date, key, body
                )
            }

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
        // CC messages show "Available limit OMR X" — that's remaining credit on the card, not
        // money in the bank. Feeding it into the balance series would corrupt every trend.
        if (body.contains("Available limit", ignoreCase = true)) return null
        val m = avlBal.find(body)
            ?: balanceArabic.find(body)
            ?: balanceEnglishAfter.find(body)
            ?: balanceEnglishBefore.find(body)
            ?: return null
        val amt = num(m.groupValues[1])
        if (amt <= 0.0) return null
        // Mobile-payment SMS carry their own timestamp; prefer it over the delivery time.
        val stamped = englishDateTime.find(body)?.groupValues?.get(1)?.let { parseDmyHms(it) } ?: 0L
        return BalanceInfo(amt, if (stamped > 0) stamped else date)
    }

    /**
     * A payment landing on the credit card. This is NOT spending (it's the checking account
     * paying down the card), but it raises the available limit — so [CardCharges] needs it to
     * reconstruct what a foreign transaction actually cost.
     */
    fun parseCardPayment(rawBody: String, date: Long): CardPaymentInfo? {
        val body = normalize(rawBody)
        val m = ccPayment.find(body) ?: return null
        val amt = num(m.groupValues[1])
        if (amt <= 0.0) return null
        val stamped = englishDateTime.find(body)?.groupValues?.get(1)?.let { parseDmyHms(it) } ?: 0L
        return CardPaymentInfo(m.groupValues[2].takeLast(4), amt, if (stamped > 0) stamped else date)
    }

    /** The remaining credit reported by a card spend SMS — drives the "owed on card" figure. */
    fun parseCardLimit(rawBody: String, date: Long): CardLimitInfo? {
        val body = normalize(rawBody)
        val spend = ccSpend.find(body) ?: return null
        val limit = ccAvailableLimit.find(body)?.groupValues?.get(1)?.let { num(it) } ?: return null
        if (limit <= 0.0) return null
        val stamped = parseDmyHms(spend.groupValues[5])
        return CardLimitInfo(spend.groupValues[1].takeLast(4), limit, if (stamped > 0) stamped else date)
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

    /** Parses a "dd/MM/yyyy HH:mm:ss" timestamp (used in CC spend SMS) to epoch millis, or 0. */
    private fun parseDmyHms(s: String): Long = try {
        val clean = s.trim()
        val datePart = clean.substringBefore(" ")
        val timePart = clean.substringAfter(" ")
        val d = datePart.split("/")
        val t = timePart.split(":")
        java.util.Calendar.getInstance().apply {
            clear()
            set(d[2].toInt(), d[1].toInt() - 1, d[0].toInt(),
                t[0].toInt(), t[1].toInt(), t[2].toInt())
        }.timeInMillis
    } catch (e: Exception) { 0L }
}

/** The running account balance an SMS reported, at the time it was sent. */
data class BalanceInfo(val balance: Double, val date: Long)

/** An Annual General Meeting invite extracted from an SMS. */
data class AgmInfo(val company: String, val meetingDate: Long, val smsDate: Long)

/** An IPO subscription-request confirmation (MCD). */
data class IpoAppInfo(val reference: String, val date: Long)
