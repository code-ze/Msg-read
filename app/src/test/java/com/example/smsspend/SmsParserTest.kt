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
}
