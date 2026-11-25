package com.smashsms.filter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

object ContactChecker {
    private const val TAG = "ContactChecker"

    fun hasReadContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks whether [phoneNumber] belongs to a saved contact via [ContactsContract.PhoneLookup].
     * Returns true if READ_CONTACTS is missing or on query errors (fail-safe so contacts are never
     * mistakenly deleted).
     */
    fun isContact(context: Context, phoneNumber: String?): Boolean {
        if (!hasReadContactsPermission(context)) {
            Log.w(TAG, "READ_CONTACTS not granted; treating sender as protected")
            return true
        }
        val normalized = phoneNumber?.trim().orEmpty()
        if (normalized.isEmpty()) return false

        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalized),
        )
        return try {
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException querying PhoneLookup", e)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error querying PhoneLookup for $normalized", e)
            false
        }
    }

    /**
     * Fallback check for notification titles where the messaging app displays a contact's
     * display name rather than their phone number.
     */
    fun isContactByDisplayName(context: Context, displayName: String?): Boolean {
        if (!hasReadContactsPermission(context)) return true
        val trimmed = displayName?.trim().orEmpty()
        if (trimmed.isEmpty()) return false

        return try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ? COLLATE NOCASE",
                arrayOf(trimmed),
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Error querying Contacts display name for $trimmed", e)
            false
        }
    }

    internal fun looksLikePhoneNumber(value: String): Boolean {
        val stripped = value.trim()
        return stripped.any { it.isDigit() } && stripped.all {
            it.isDigit() || it in setOf('+', '-', '(', ')', ' ', '.')
        }
    }
}
