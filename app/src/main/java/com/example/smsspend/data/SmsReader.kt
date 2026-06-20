package com.example.smsspend.data

import android.content.Context
import android.provider.Telephony
import com.example.smsspend.parser.AgmInfo
import com.example.smsspend.parser.IpoAppInfo
import com.example.smsspend.parser.ParsedTxn
import com.example.smsspend.parser.SmsParser

/** Everything extracted from one pass over the SMS inbox. */
data class SmsScan(
    val txns: List<ParsedTxn>,
    val agms: List<AgmInfo>,
    val ipoApps: List<IpoAppInfo>
)

/** Reads the SMS inbox (READ_SMS) and extracts transactions + investment info. On-device only. */
object SmsReader {

    fun read(context: Context): SmsScan {
        val txns = ArrayList<ParsedTxn>()
        val agms = ArrayList<AgmInfo>()
        val ipoApps = ArrayList<IpoAppInfo>()

        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val cols = arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cursor = context.contentResolver.query(
            uri, cols, null, null, Telephony.Sms.DATE + " DESC"
        ) ?: return SmsScan(txns, agms, ipoApps)

        cursor.use { c ->
            val bi = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val di = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val body = c.getString(bi) ?: continue
                val date = c.getLong(di)
                SmsParser.parse(body, date)?.let { txns.add(it) }
                SmsParser.parseAgm(body, date)?.let { agms.add(it) }
                SmsParser.parseIpoApplication(body, date)?.let { ipoApps.add(it) }
            }
        }
        return SmsScan(txns, agms, ipoApps)
    }
}
