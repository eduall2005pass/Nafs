package com.nafsshield.util

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

class PinManager(context: Context) {

    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            Constants.PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback: EncryptedSharedPreferences key corrupt হলে clear করে নতুন তৈরি করো
        context.getSharedPreferences(Constants.PREF_FILE + "_backup", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            Constants.PREF_FILE + "_v2",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val isPinSetup: Boolean
        get() = prefs.getBoolean(Constants.KEY_PIN_SETUP_DONE, false)

    fun setPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(Constants.KEY_PIN_HASH, hash)
            .putString(Constants.KEY_PIN_SALT, salt)
            .putBoolean(Constants.KEY_PIN_SETUP_DONE, true)
            .putInt(Constants.KEY_FAILED_ATTEMPTS, 0)
            .apply()
    }

    fun verifyPin(inputPin: String): PinResult {
        val lockoutUntil = prefs.getLong(Constants.KEY_LOCKOUT_UNTIL, 0L)
        if (System.currentTimeMillis() < lockoutUntil) {
            val remaining = (lockoutUntil - System.currentTimeMillis()) / 1000
            return PinResult.LockedOut(remaining)
        }
        val storedHash = prefs.getString(Constants.KEY_PIN_HASH, null) ?: return PinResult.NoPinSet
        val salt = prefs.getString(Constants.KEY_PIN_SALT, null) ?: return PinResult.NoPinSet
        val inputHash = hashPin(inputPin, salt)

        return if (inputHash == storedHash) {
            prefs.edit()
                .putInt(Constants.KEY_FAILED_ATTEMPTS, 0)
                .putLong(Constants.KEY_LOCKOUT_UNTIL, 0L)
                .apply()
            PinResult.Correct
        } else {
            val attempts = prefs.getInt(Constants.KEY_FAILED_ATTEMPTS, 0) + 1
            prefs.edit().putInt(Constants.KEY_FAILED_ATTEMPTS, attempts).apply()
            if (attempts >= Constants.MAX_FAILED_ATTEMPTS) {
                val lockUntil = System.currentTimeMillis() + Constants.LOCKOUT_DURATION_MS
                prefs.edit()
                    .putLong(Constants.KEY_LOCKOUT_UNTIL, lockUntil)
                    .putInt(Constants.KEY_FAILED_ATTEMPTS, 0)
                    .apply()
                PinResult.LockedOut(Constants.LOCKOUT_DURATION_MS / 1000)
            } else {
                PinResult.Wrong(Constants.MAX_FAILED_ATTEMPTS - attempts)
            }
        }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun hashPin(pin: String, salt: String): String {
        val input = "$pin:$salt"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    var isMasterEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_MASTER_ENABLED, false)
        set(v) { prefs.edit().putBoolean(Constants.KEY_MASTER_ENABLED, v).apply() }

    var isVpnEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_VPN_ENABLED, false)
        set(v) { prefs.edit().putBoolean(Constants.KEY_VPN_ENABLED, v).apply() }

    var isOcrEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_KEYWORD_OCR, false)
        set(v) { prefs.edit().putBoolean(Constants.KEY_KEYWORD_OCR, v).apply() }

    var primaryDns: String
        get() = prefs.getString(Constants.KEY_DNS_PRIMARY, Constants.DEFAULT_DNS_PRIMARY)!!
        set(v) { prefs.edit().putString(Constants.KEY_DNS_PRIMARY, v).apply() }

    var secondaryDns: String
        get() = prefs.getString(Constants.KEY_DNS_SECONDARY, Constants.DEFAULT_DNS_SECONDARY)!!
        set(v) { prefs.edit().putString(Constants.KEY_DNS_SECONDARY, v).apply() }
}

sealed class PinResult {
    object Correct   : PinResult()
    object NoPinSet  : PinResult()
    data class Wrong(val attemptsLeft: Int)      : PinResult()
    data class LockedOut(val secondsRemaining: Long) : PinResult()
}
