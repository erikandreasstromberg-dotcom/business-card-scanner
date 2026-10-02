package com.houseofsilver.cardscanner.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TemplatePrefs(context: Context) {

    companion object {
        private const val FILE_NAME = "card_scanner_secure_prefs"
        private const val KEY_SUBJECT = "email.subject_template"
        private const val KEY_BODY = "email.body_template"
        private const val KEY_VERSION = "email.template_version"
        private const val KEY_PREFERRED_EMAIL_PKG = "email.preferred_package"

        const val DEFAULT_SUBJECT = "Nice to meet you, {first_name}"

        val DEFAULT_BODY = """
Hi {first_name},

Great meeting you. I run House of Silver, a boutique AI adoption consultancy based in Stockholm. We help businesses adopt AI in sovereign, practical ways: advisory, training, and light automation with locally hosted models when sovereignty matters.

Given your work at {company}, I thought it might be worth staying in touch. You can read more about what we do at houseofsilver.ai.

Best,
Erik
        """.trimIndent()
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

    fun getSubject(): String = prefs.getString(KEY_SUBJECT, DEFAULT_SUBJECT) ?: DEFAULT_SUBJECT
    fun getBody(): String = prefs.getString(KEY_BODY, DEFAULT_BODY) ?: DEFAULT_BODY
    fun getVersion(): Int = prefs.getInt(KEY_VERSION, 1)

    fun save(subject: String, body: String) {
        prefs.edit()
            .putString(KEY_SUBJECT, subject)
            .putString(KEY_BODY, body)
            .putInt(KEY_VERSION, getVersion() + 1)
            .apply()
    }

    fun resetToDefault() {
        prefs.edit()
            .putString(KEY_SUBJECT, DEFAULT_SUBJECT)
            .putString(KEY_BODY, DEFAULT_BODY)
            .putInt(KEY_VERSION, getVersion() + 1)
            .apply()
    }

    /** Empty string means "ask each time via chooser". */
    fun getPreferredEmailPackage(): String = prefs.getString(KEY_PREFERRED_EMAIL_PKG, "") ?: ""

    fun savePreferredEmailPackage(pkg: String) {
        prefs.edit().putString(KEY_PREFERRED_EMAIL_PKG, pkg).apply()
    }
}
