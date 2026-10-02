package com.houseofsilver.cardscanner.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.houseofsilver.cardscanner.domain.model.Contact

/** The user's own contact details, shown to others as a QR code and/or a card photo. */
class OwnCardPrefs(context: Context) {

    companion object {
        private const val FILE_NAME = "card_scanner_own_card"
        private const val KEY_NAME = "own.name"
        private const val KEY_EMAIL = "own.email"
        private const val KEY_COMPANY = "own.company"
        private const val KEY_ROLE = "own.role"
        private const val KEY_PHONE = "own.phone"
        private const val KEY_WEBSITE = "own.website"
        private const val KEY_LINKEDIN = "own.linkedin"
        private const val KEY_IMAGE_URI = "own.image_uri"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getContact(): Contact = Contact(
        name = prefs.getString(KEY_NAME, "") ?: "",
        email = prefs.getString(KEY_EMAIL, "") ?: "",
        company = prefs.getString(KEY_COMPANY, "")?.takeIf { it.isNotBlank() },
        role = prefs.getString(KEY_ROLE, "")?.takeIf { it.isNotBlank() },
        phone = prefs.getString(KEY_PHONE, "")?.takeIf { it.isNotBlank() },
        website = prefs.getString(KEY_WEBSITE, "")?.takeIf { it.isNotBlank() },
        linkedin = prefs.getString(KEY_LINKEDIN, "")?.takeIf { it.isNotBlank() },
        address = null
    )

    fun save(contact: Contact) {
        prefs.edit()
            .putString(KEY_NAME, contact.name)
            .putString(KEY_EMAIL, contact.email)
            .putString(KEY_COMPANY, contact.company ?: "")
            .putString(KEY_ROLE, contact.role ?: "")
            .putString(KEY_PHONE, contact.phone ?: "")
            .putString(KEY_WEBSITE, contact.website ?: "")
            .putString(KEY_LINKEDIN, contact.linkedin ?: "")
            .apply()
    }

    fun getImageUri(): String? = prefs.getString(KEY_IMAGE_URI, "")?.takeIf { it.isNotBlank() }

    fun saveImageUri(uri: String?) {
        prefs.edit().putString(KEY_IMAGE_URI, uri ?: "").apply()
    }
}
