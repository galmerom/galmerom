package com.galmerom.quickdial.data

import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Data
import com.galmerom.quickdial.domain.ContactIdentity
import com.galmerom.quickdial.domain.stableContactId
import com.galmerom.quickdial.domain.whatsAppDigits

data class DevicePhone(
    val number: String,
    val type: Int,
    val customLabel: String?,
    val superPrimary: Boolean,
    val primary: Boolean,
)

data class DeviceContact(
    val id: String,
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val photoUri: String?,
    val phones: List<DevicePhone>,
)

sealed interface WhatsAppTarget {
    /** The contact's own WhatsApp row. Open this URI; do not search by name. */
    data class Profile(
        val dataUri: String,
        val mimeType: String,
        val packageName: String,
    ) : WhatsAppTarget

    /** No WhatsApp row. Open wa.me with the selected number, already in international digits. */
    data class Phone(
        val digits: String,
        val packageName: String,
    ) : WhatsAppTarget
}

class PhoneBook(private val context: Context) {
    fun loadAll(): List<DeviceContact> {
        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.LOOKUP_KEY,
            Phone.DISPLAY_NAME,
            Phone.NUMBER,
            Phone.NORMALIZED_NUMBER,
            Phone.TYPE,
            Phone.LABEL,
            Phone.IS_PRIMARY,
            Phone.IS_SUPER_PRIMARY,
            Phone.PHOTO_THUMBNAIL_URI,
        )
        val grouped = linkedMapOf<String, Builder>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${Phone.DISPLAY_NAME} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(Phone.CONTACT_ID)
            val lookupIndex = cursor.getColumnIndex(Phone.LOOKUP_KEY)
            val nameIndex = cursor.getColumnIndex(Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(Phone.NUMBER)
            val normalizedIndex = cursor.getColumnIndex(Phone.NORMALIZED_NUMBER)
            val typeIndex = cursor.getColumnIndex(Phone.TYPE)
            val labelIndex = cursor.getColumnIndex(Phone.LABEL)
            val primaryIndex = cursor.getColumnIndex(Phone.IS_PRIMARY)
            val superIndex = cursor.getColumnIndex(Phone.IS_SUPER_PRIMARY)
            val photoIndex = cursor.getColumnIndex(Phone.PHOTO_THUMBNAIL_URI)
            if (idIndex < 0 || numberIndex < 0) return emptyList()
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIndex)
                val lookupKey = cursor.getStringOrNull(lookupIndex).orEmpty()
                val stableId = lookupKey.ifBlank { "id:$contactId" }
                val rawNumber = cursor.getStringOrNull(normalizedIndex)?.takeIf { it.isNotBlank() }
                    ?: cursor.getStringOrNull(numberIndex).orEmpty()
                if (rawNumber.isBlank()) continue
                val builder = grouped.getOrPut(stableId) {
                    Builder(
                        id = stableId,
                        contactId = contactId,
                        lookupKey = lookupKey,
                        displayName = cursor.getStringOrNull(nameIndex).orEmpty().ifBlank { rawNumber },
                        photoUri = cursor.getStringOrNull(photoIndex),
                    )
                }
                if (builder.photoUri.isNullOrBlank()) builder.photoUri = cursor.getStringOrNull(photoIndex)
                if (builder.displayName.isBlank()) {
                    builder.displayName = cursor.getStringOrNull(nameIndex).orEmpty()
                }
                builder.phones += DevicePhone(
                    number = cursor.getStringOrNull(numberIndex)?.takeIf { it.isNotBlank() } ?: rawNumber,
                    type = if (typeIndex >= 0) cursor.getInt(typeIndex) else Phone.TYPE_OTHER,
                    customLabel = cursor.getStringOrNull(labelIndex),
                    superPrimary = superIndex >= 0 && cursor.getInt(superIndex) == 1,
                    primary = primaryIndex >= 0 && cursor.getInt(primaryIndex) == 1,
                )
            }
        }
        return grouped.values.map { it.build() }
    }

    fun findWhatsApp(contactId: Long, lookupKey: String, fallbackNumber: String): WhatsAppTarget? {
        val resolvedId = resolveContactId(contactId, lookupKey) ?: contactId
        val selection = "${Data.CONTACT_ID}=? AND ${Data.MIMETYPE} IN (?, ?)"
        val args = arrayOf(resolvedId.toString(), WHATSAPP, WHATSAPP_BUSINESS)
        var business: WhatsAppTarget.Profile? = null
        context.contentResolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.MIMETYPE),
            selection,
            args,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(Data._ID)
            val mimeIndex = cursor.getColumnIndex(Data.MIMETYPE)
            while (cursor.moveToNext()) {
                if (idIndex < 0) continue
                val mime = cursor.getStringOrNull(mimeIndex) ?: continue
                val rowId = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(Data.CONTENT_URI, rowId).toString()
                val profile = if (mime == WHATSAPP_BUSINESS) {
                    WhatsAppTarget.Profile(uri, mime, "com.whatsapp.w4b")
                } else {
                    WhatsAppTarget.Profile(uri, WHATSAPP, "com.whatsapp")
                }
                if (mime == WHATSAPP) return profile
                business = profile
            }
        }
        business?.let { return it }
        val digits = whatsAppDigits(fallbackNumber) ?: return null
        return WhatsAppTarget.Phone(digits, "com.whatsapp")
    }

    /** Follow an old id or lookup key to the contact as it exists now. */
    fun currentIdentity(contactId: Long, lookupKey: String): ContactIdentity? {
        val lookupUri = when {
            lookupKey.isNotBlank() -> ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
            contactId > 0L -> ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            else -> null
        } ?: return null
        val resolved = if (lookupKey.isNotBlank()) {
            ContactsContract.Contacts.lookupContact(context.contentResolver, lookupUri) ?: return null
        } else {
            lookupUri
        }
        context.contentResolver.query(
            resolved,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getLong(0)
            val key = cursor.getString(1).orEmpty()
            val name = cursor.getString(2).orEmpty()
            return ContactIdentity(stableContactId(key, id), id, key, name)
        }
        return null
    }

    private fun resolveContactId(contactId: Long, lookupKey: String): Long? {
        if (lookupKey.isBlank()) return contactId
        val lookup = ContactsContract.Contacts.getLookupUri(contactId, lookupKey) ?: return contactId
        val resolved = ContactsContract.Contacts.lookupContact(context.contentResolver, lookup) ?: return null
        return runCatching { ContentUris.parseId(resolved) }.getOrNull()
    }

    private class Builder(
        val id: String,
        val contactId: Long,
        val lookupKey: String,
        var displayName: String,
        var photoUri: String?,
    ) {
        val phones = mutableListOf<DevicePhone>()

        fun build() = DeviceContact(id, contactId, lookupKey, displayName, photoUri, phones.toList())
    }

    private companion object {
        const val WHATSAPP = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
        const val WHATSAPP_BUSINESS = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile"
    }
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? =
    if (index < 0 || isNull(index)) null else getString(index)

fun phoneLabel(type: Int, customLabel: String?): String = when (type) {
    Phone.TYPE_MOBILE -> "נייד"
    Phone.TYPE_HOME -> "בית"
    Phone.TYPE_WORK -> "עבודה"
    Phone.TYPE_MAIN -> "ראשי"
    Phone.TYPE_WORK_MOBILE -> "נייד בעבודה"
    Phone.TYPE_FAX_WORK, Phone.TYPE_FAX_HOME, Phone.TYPE_OTHER_FAX -> "פקס"
    Phone.TYPE_CUSTOM -> customLabel?.takeIf { it.isNotBlank() } ?: "אחר"
    else -> customLabel?.takeIf { it.isNotBlank() } ?: "טלפון"
}
