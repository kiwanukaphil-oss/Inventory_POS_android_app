package com.kline.inventorypos.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.sessionDataStore by preferencesDataStore(name = "secure_session")

interface SessionStore {
    suspend fun readToken(): String?
    suspend fun readBranchId(): String?
    suspend fun writeToken(token: String)
    suspend fun writeBranchId(branchId: String)
    suspend fun clear()
}

class KeystoreSessionStore(private val context: Context) : SessionStore {
    private val tokenKey = stringPreferencesKey("encrypted_access_token")
    private val branchKey = stringPreferencesKey("selected_branch_id")

    override suspend fun readToken(): String? {
        val encrypted = context.sessionDataStore.data.first()[tokenKey] ?: return null
        return runCatching { decrypt(encrypted) }.getOrNull()
    }

    override suspend fun readBranchId(): String? = context.sessionDataStore.data.first()[branchKey]

    override suspend fun writeToken(token: String) {
        context.sessionDataStore.edit { it[tokenKey] = encrypt(token) }
    }

    override suspend fun writeBranchId(branchId: String) {
        context.sessionDataStore.edit { it[branchKey] = branchId }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit {
            it.remove(tokenKey)
            it.remove(branchKey)
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, payload.copyOfRange(0, IV_BYTES)),
        )
        return cipher.doFinal(payload.copyOfRange(IV_BYTES, payload.size)).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "inventory_pos_access_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
