package com.example.smsspend.data

import android.content.Context
import android.provider.Telephony
import com.example.smsspend.parser.ParsedTxn
import com.example.smsspend.parser.SmsParser

/** Reads the SMS inbox (READ_SMS) and returns parsed transactions. On-device only. */
object SmsReader {

    fun read(context: Context): List<ParsedTxn> {
        val out = ArrayList<ParsedTxn>()
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val cols = arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cursor = context.contentResolver.query(
            uri, cols, null, null, Telephony.Sms.DATE + " DESC"
        ) ?: return out
        cursor.use { c ->
            val bi = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val di = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val body = c.getString(bi) ?: continue
                if (!SmsParser.looksRelevant(body)) continue
                val date = c.getLong(di)
                SmsParser.parse(body, date)?.let { out.add(it) }
            }
        }
        return out
    }
}
