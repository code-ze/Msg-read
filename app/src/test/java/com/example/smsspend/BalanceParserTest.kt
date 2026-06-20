package com.example.smsspend

import com.example.smsspend.parser.SmsParser
import com.example.smsspend.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceParserTest {

    private val salarySms =
        "تم إيداع 1679.000 OMR في حسابك رقم 0311XXXXXXXX0018 بتاريخ  2026/05/21 10:03:26. " +
            "رصيدك الحالي هو 8781.887 OMR."

    @Test fun extractsRunningBalanceFromArabicSms() {
        val b = SmsParser.parseBalance(salarySms, 1000L)
        assertNotNull(b)
        assertEquals(8781.887, b!!.balance, 0.0001)
        assertEquals(1000L, b.date)
    }

    @Test fun salaryDepositAmountIsNotTheBalance() {
        // The deposit amount (1679) must be parsed independently of the running balance.
        val txn = SmsParser.parse(salarySms, 1000L)
        assertNotNull(txn)
        assertEquals(TxnType.DEPOSIT, txn!!.type)
        assertEquals(1679.0, txn.amount, 0.0001)
    }

    @Test fun extractsEnglishBalance() {
        val b = SmsParser.parseBalance("Your available balance is OMR 250.500", 5L)
        assertNotNull(b)
        assertEquals(250.500, b!!.balance, 0.0001)
    }

    @Test fun noBalanceWhenAbsent() {
        assertNull(SmsParser.parseBalance("لقد استلمت OMR 5.000 من AHMED في حسابك", 5L))
    }
}
