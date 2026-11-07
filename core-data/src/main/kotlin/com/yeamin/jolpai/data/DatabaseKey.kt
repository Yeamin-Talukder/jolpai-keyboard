package com.yeamin.jolpai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Jolpai's encryption keys.
 *
 * A random 256-bit key encrypts the database (SQLCipher). That key is itself stored only in
 * wrapped (encrypted) form, under an AES key that lives in the Android Keystore: in the phone's
 * secure hardware where available, never readable by any app, and lost if the app is
 * uninstalled. Copying Jolpai's files off the phone therefore yields nothing readable.
 *
 * The same Keystore key encrypts clipboard pictures ([encrypt]/[decrypt]).
 */
object DatabaseKey {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "jolpai_data_key"
    private const val PREFS = "db_key"
    private const val WRAPPED = "wrapped"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    /** True when a database key exists (a database may already be encrypted with it). */
    fun exists(context: Context): Boolean = prefs(context).contains(WRAPPED)

    /** The database key; created on first use. Throws if a stored key can no longer be unwrapped. */
    @Synchronized
    fun get(context: Context): ByteArray {
        val stored = prefs(context).getString(WRAPPED, null)
        if (stored != null) return decrypt(Base64.decode(stored, Base64.NO_WRAP))
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs(context).edit().putString(WRAPPED, Base64.encodeToString(encrypt(key), Base64.NO_WRAP)).commit()
        return key
    }

    /** Forgets the database key (only when the database it opened is being deleted). */
    @Synchronized
    fun reset(context: Context) {
        prefs(context).edit().remove(WRAPPED).commit()
    }

    /** AES-GCM with the Keystore key: returns IV followed by ciphertext and tag. */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, data, 0, IV_BYTES))
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
