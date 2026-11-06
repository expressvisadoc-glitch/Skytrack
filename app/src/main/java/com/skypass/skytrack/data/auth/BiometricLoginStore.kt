package com.skypass.skytrack.data.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Kept apart from the session store so biometric sign-in survives sign-out (that is its purpose)
private val Context.biometricDataStore by preferencesDataStore(name = "skytrack_biometric")

/** The account that biometric sign-in is set up for on this device */
data class BiometricAccount(val employeeId: String)

/**
 * Biometric sign-in.
 *
 * The password is encrypted with an AES key that lives in the Android Keystore (secure hardware where available).
 * The key can only be used right after a successful strong biometric check (fingerprint), is never exported, and
 * is destroyed by Android when a new fingerprint is enrolled. Only the encrypted password and its IV are stored.
 * Signing in with biometrics decrypts the password and performs the normal sign-in, so a password reset by an
 * admin or a deactivated account stops biometric sign-in as well.
 */
@Singleton
class BiometricLoginStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val employeeId = stringPreferencesKey("employee_id")
        val ciphertext = stringPreferencesKey("ciphertext")
        val iv = stringPreferencesKey("iv")
    }

    /** Raised when the Keystore key no longer exists or was invalidated (e.g. a new fingerprint was enrolled) */
    class KeyInvalidatedException : Exception()

    val account: Flow<BiometricAccount?> = context.biometricDataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
        .map { prefs ->
            val id = prefs[Keys.employeeId]
            if (id.isNullOrBlank() || prefs[Keys.ciphertext].isNullOrBlank() || prefs[Keys.iv].isNullOrBlank()) null
            else BiometricAccount(id)
        }

    suspend fun currentAccount(): BiometricAccount? = account.first()

    /** A fresh key and a cipher ready to encrypt; the biometric prompt unlocks it */
    fun encryptCipher(): Cipher {
        deleteKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey()) }
    }

    /** A cipher ready to decrypt the saved password; the biometric prompt unlocks it */
    suspend fun decryptCipher(): Cipher {
        val prefs = context.biometricDataStore.data.first()
        val iv = prefs[Keys.iv]?.let { Base64.decode(it, Base64.NO_WRAP) } ?: throw KeyInvalidatedException()
        val key = existingKey() ?: throw KeyInvalidatedException()
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw KeyInvalidatedException()
        }
    }

    /** Saves the password using a cipher unlocked by the biometric prompt */
    suspend fun save(employeeId: String, password: String, unlockedCipher: Cipher) {
        val ciphertext = unlockedCipher.doFinal(password.toByteArray(Charsets.UTF_8))
        context.biometricDataStore.edit { prefs ->
            prefs[Keys.employeeId] = employeeId.trim().uppercase()
            prefs[Keys.ciphertext] = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            prefs[Keys.iv] = Base64.encodeToString(unlockedCipher.iv, Base64.NO_WRAP)
        }
    }

    /** Reads the password using a cipher unlocked by the biometric prompt */
    suspend fun readPassword(unlockedCipher: Cipher): String {
        val encoded = context.biometricDataStore.data.first()[Keys.ciphertext] ?: throw KeyInvalidatedException()
        return String(unlockedCipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    /** Turns biometric sign-in off: removes the saved password and destroys the key */
    suspend fun clear() {
        context.biometricDataStore.edit { it.clear() }
        deleteKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = runCatching { keyStore().getKey(KEY_ALIAS, null) as? SecretKey }.getOrNull()

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun createKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Every use needs its own strong biometric check (no time window)
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "skytrack_biometric_login"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
