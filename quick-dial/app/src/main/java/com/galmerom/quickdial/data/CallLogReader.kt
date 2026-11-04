package com.galmerom.quickdial.data

import android.content.Context
import android.provider.CallLog
import com.galmerom.quickdial.domain.CallDirection
import com.galmerom.quickdial.domain.CallRecord

class CallLogReader(private val context: Context) {
    fun readOutgoingSince(sinceMillis: Long): List<CallRecord> {
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NORMALIZED_NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
        )
        val records = mutableListOf<CallRecord>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            "${CallLog.Calls.DATE}>=?",
            arrayOf(sinceMillis.toString()),
            null,
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val normalizedIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NORMALIZED_NUMBER)
            val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
            if (typeIndex < 0 || dateIndex < 0) return emptyList()
            while (cursor.moveToNext()) {
                val normalized = if (normalizedIndex >= 0 && !cursor.isNull(normalizedIndex)) {
                    cursor.getString(normalizedIndex)
                } else {
                    null
                }
                val number = normalized?.takeIf { it.isNotBlank() }
                    ?: if (numberIndex >= 0 && !cursor.isNull(numberIndex)) cursor.getString(numberIndex) else ""
                if (number.isNullOrBlank()) continue
                val direction = when (cursor.getInt(typeIndex)) {
                    CallLog.Calls.OUTGOING_TYPE -> CallDirection.Outgoing
                    CallLog.Calls.INCOMING_TYPE -> CallDirection.Incoming
                    CallLog.Calls.MISSED_TYPE -> CallDirection.Missed
                    else -> CallDirection.Other
                }
                records += CallRecord(number, direction, cursor.getLong(dateIndex))
            }
        }
        return records
    }
}
