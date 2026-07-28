package com.example.smsspend

import com.example.smsspend.parser.SmsParser
import com.example.smsspend.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private val date = 1_700_000_000_000L

    @Test fun parsesIpoSubscription() {
        val body = "Dear Customer, Your AC # 0311XXXXXXXX0018 has been debited for amount of " +
            "OMR 624.000 as subscription of IPO OMAN INDIA FERTILISER COMPANY OMIF for " +
            "Investor Account LFT261694Z8173W2"
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        assertEquals(TxnType.IPO, t.type)
        assertEquals(624.0, t.amount, 0.0001)
        assertTrue(t.merchantRaw.contains("OMAN INDIA FERTILISER COMPANY"))
    }

    @Test fun parsesDividend() {
        val body = "تم إيداع 70.528 OMR في حسابك رقم 0311XXXXXXXX0018 عن طريق 000233884 " +
            "DIV payment-OQ EXPLORATIO بتاريخ  2025/06/01 21:48:22. رصيدك الحالي هو 5858.791 OMR."
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        assertEquals(TxnType.DIVIDEND, t.type)
        // first amount is the deposit, NOT the trailing balance
        assertEquals(70.528, t.amount, 0.0001)
        assertEquals("OQ EXPLORATIO", t.merchantRaw)
    }

    @Test fun parsesDirectDebitCard() {
        val body = "تم خصم 3.300 OMR من حسابك بواسطة بطاقة الخصم المباشر في TALABAT بتاريخ 01/06/2025"
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals(3.300, t.amount, 0.0001)
        assertEquals("TALABAT", t.merchantRaw)
    }

    @Test fun parsesWalletSent() {
        val body = "لقد قمت بإرسال OMR 5.000 إلى AHMED من حسابك"
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        assertEquals(TxnType.WALLET_OUT, t.type)
        assertEquals(5.0, t.amount, 0.0001)
        assertEquals("AHMED", t.merchantRaw)
    }

    @Test fun parsesWalletReceived() {
        val body = "لقد استلمت OMR 10.000 من SALIM في حسابك"
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        assertEquals(TxnType.WALLET_IN, t.type)
        assertEquals(10.0, t.amount, 0.0001)
        assertEquals("SALIM", t.merchantRaw)
    }

    @Test fun parsesGenericDeposit() {
        val body = "تم إيداع 100.000 OMR في حسابك رقم 0311XXXXXXXX0018"
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        assertEquals(TxnType.DEPOSIT, t.type)
        assertEquals(100.0, t.amount, 0.0001)
    }

    @Test fun parsesGenericDebit() {
        val body = "تم خصم OMR 3.300 من حسابك رقم 0311XXXX في LULU بتاريخ 01/06/2025"
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals(3.300, t.amount, 0.0001)
        assertEquals("LULU", t.merchantRaw)
    }

    @Test fun ignoresUnrelated() {
        assertEquals(null, SmsParser.parse("Get instant financing via Bank Nizwa App", date))
    }

    // ---- Credit card (English) patterns ----

    @Test fun parsesCcSpendOmr() {
        val body = "Card 420460******0444 used for OMR 139.500 at HOLLISTER CALIFORNIA  on 28/07/2026 12:33:39. Available limit OMR 928.448."
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals(139.5, t.amount, 0.0001)
        assertEquals("HOLLISTER CALIFORNIA", t.merchantRaw)
    }

    @Test fun parsesCcSpendUsd() {
        val body = "Card 420460******0444 used for USD 790.420 at Cars on Booking  on 27/07/2026 20:04:03. Available limit OMR 636.448."
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals(790.420, t.amount, 0.0001)
        // USD amounts get currency tag appended
        assertTrue(t.merchantRaw.contains("Cars on Booking"))
        assertTrue(t.merchantRaw.contains("USD"))
    }

    @Test fun ignoresCcPaymentCredited() {
        val body = "Payment of OMR 200.000 has been credited on your card 420460******0444 on 28/07/2026 13:39:57"
        assertEquals(null, SmsParser.parse(body, date))
    }

    @Test fun ignoresAccountDebitedForCcPayment() {
        val body = "Dear Customer, Your a/c no 0311XXXXXXXX0018 has been debited for an amt of OMR 200.000 as payment towards bank muscat Credit Card No. 4204XXXXXXXX0444"
        assertEquals(null, SmsParser.parse(body, date))
    }

    @Test fun ignoresCcReversal() {
        val body = "your card 420460******0444 transaction for USD 1.000 at GOOGLE*ANDROID TEMP on 27/07/2026 19:40:35 was reversed"
        assertEquals(null, SmsParser.parse(body, date))
    }

    @Test fun ignoresOtp() {
        val body = "Your One Time Password (OTP): 5631 for transaction done at TMDONE for OMR 3.800 using card ending 0444 is valid for 5 minutes. Please do not share this OTP with anyone."
        assertEquals(null, SmsParser.parse(body, date))
    }

    @Test fun ccSpendUsesTransactionTimestamp() {
        val body = "Card 420460******0444 used for OMR 33.000 at LC WAIKIKI  on 28/07/2026 12:56:00. Available limit OMR 895.448."
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        // Transaction date from the SMS body (2026-07-28 12:56:00 local) must be set,
        // not the SMS delivery epoch; just verify it's a plausible non-zero value.
        assertTrue("date should come from SMS body", t.date > 0 && t.date != date)
    }
}
