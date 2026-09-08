package com.charikot.parentlock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

object PinManager {
    private const val ALIAS = "charikot_parent_pin_hmac_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey()
    }

    private fun digest(pin: String, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key())
        mac.update(salt)
        return mac.doFinal(pin.toByteArray(Charsets.UTF_8))
    }

    fun setPin(pin: String): Boolean {
        if (!(pin.length == 4 || pin.length == 6) || !pin.all { it.isDigit() }) return false
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        AppPrefs.storePin(salt, digest(pin, salt))
        return true
    }

    fun verify(pin: String): Boolean {
        val salt = AppPrefs.pinSalt() ?: return false
        val expectedString = AppPrefs.pinHash() ?: return false
        return try {
            val expected = Base64.decode(expectedString, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, digest(pin, salt))
        } catch (_: Exception) { false }
    }
}
