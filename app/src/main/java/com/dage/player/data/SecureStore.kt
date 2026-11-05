package com.dage.player.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 的 AES-GCM 加密敏感字符串（API Key、NAS 密码）。
 * 密钥不出安全硬件，SharedPreferences 里只存 IV+密文的 Base64。
 * 不同用途用不同别名，互不影响；已有别名不能改，否则老密文解不开。
 */
object SecureStore {

    /** AI API Key 用的别名（历史值，勿改） */
    const val ALIAS_API_KEY = "dage_api_key"

    /** NAS 密码用的别名 */
    const val ALIAS_NAS_PASS = "dage_nas_pass"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private fun keyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(alias: String): SecretKey {
        val ks = keyStore()
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String, alias: String = ALIAS_API_KEY): String? = runCatching {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key(alias))
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }.getOrNull()

    fun decrypt(encoded: String, alias: String = ALIAS_API_KEY): String? = runCatching {
        if (encoded.isEmpty()) return ""
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        if (raw.size < 13) return ""
        val iv = raw.copyOfRange(0, 12)
        val body = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()

    /** 换机/送人前一键抹掉：删除 Keystore 里的密钥，已存的密文就再也解不开 */
    fun wipe() {
        arrayOf(ALIAS_API_KEY, ALIAS_NAS_PASS).forEach { a ->
            runCatching { keyStore().deleteEntry(a) }
        }
    }
}
