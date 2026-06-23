package com.example.smsspend

import com.example.smsspend.parser.SmsParser
import com.example.smsspend.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McdParserTest {
    private val date = 1_700_000_000_000L

    @Test fun mcdCashDividend() {
        val body = "‫المساهم‬‪ أمجد بدر حمود الخروصي ‬‫ بالرقم ‬‪ (000233884) ‬‫تم إرسال مبلغ‬‪ 44.37 OMR ‬‫ نتيجة لـ ‬‪ أرباح نقدية ‬‫ لشركة ‬‪ اوكيو للصناعات الاساسية المنطقة الحرة بصلالة ‬"
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        assertEquals(TxnType.DIVIDEND, t.type)
        assertEquals(44.37, t.amount, 0.001)
        assertTrue("merchant was: '${t.merchantRaw}'", t.merchantRaw.contains("اوكيو للصناعات"))
    }

    @Test fun mcdCashDividend2() {
        val body = "‫المساهم‬‪ أمجد بدر حمود الخروصي ‬‫ بالرقم ‬‪ (000233884) ‬‫تم إرسال مبلغ‬‪ 70.724 OMR ‬‫ نتيجة لـ ‬‪ أرباح نقدية ‬‫ لشركة ‬‪ اوكيو للاستكشاف ‬"
        val t = SmsParser.parse(body, date)
        assertNotNull(t); t!!
        assertEquals(70.724, t.amount, 0.001)
        assertEquals("اوكيو للاستكشاف", t.merchantRaw)
    }

    @Test fun agmInvite() {
        val body = "Dear Investor 000233884, OQ GAS NETWORKS SAOG invites you to attend the Annual General Meeting, On 25/03/2026  at 07:00 PM  using eAGM Platform."
        val a = SmsParser.parseAgm(body, date)
        assertNotNull(a); a!!
        assertEquals("OQ GAS NETWORKS SAOG", a.company)
        assertTrue(a.meetingDate > 0)
    }

    @Test fun ipoApplication() {
        val body = "عزيزي المستثمر صاحب الرقم \"OM00D00023388400\"‬‪ تم تسجيل طلب الإكتتاب رقم \"BM01‬‪-‬‪7280‬‪\" بنجاح. مسقط للمقاصة والإيداع"
        val i = SmsParser.parseIpoApplication(body, date)
        assertNotNull(i); i!!
        assertEquals("BM01-7280", i.reference)
    }
}
