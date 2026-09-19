package com.baixi.app.data.backup

import android.util.Base64
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * 网盘认证备份的 AES 加密/解密：
 * - 密钥：PBKDF2WithHmacSHA256（16 字节随机盐 + 210000 次迭代）从用户密码派生；
 * - 加密：AES/GCM/NoPadding（128 位 tag，认证加密，密文被篡改会解密失败）；
 * - 格式：Base64(魔数 "YUNX_AUTH_V2" + salt(16) + iv(12) + ciphertext)。
 * - 解密兼容旧版 V1（10000 次迭代）备份。
 * 密码错误 / 文件被篡改 → 解密抛异常（AEADBadTagException），上层提示「密码错误，解密失败」。
 *
 * ★ KDF 兼容（API 23-25）：
 * Android 8.0（API 26）以下没有 PBKDF2WithHmacSHA256，只有 PBKDF2WithHmacSHA1，
 * 因此这里做算法可用性探测：能拿到 SHA256 就用 SHA256（安全强度不变），
 * 否则降级 SHA1。由于备份格式（YUNX_AUTH_V2）里没有记录 KDF 且不能改动旧格式
 * （否则旧版本 App 读不了新备份），解密时先用「当前可用算法」试一次，
 * 失败（AEADBadTagException / 解密异常）再用另一个算法重试一次，两次都失败才抛原异常。
 * 影响：
 * - API 26+ 生成的新备份默认仍是 SHA256，行为与改动前完全一致；
 * - API 23-25 生成的新备份实际用 SHA1，在 API 26+ 设备上靠「重试」也能正常解密；
 * - 由 API 26+（SHA256）生成的备份在 API 23-25 设备上无法解密（这些设备没有 SHA256 实现），
 *   只能提示用户在较新系统上导入；
 * - 旧备份（V1/V2、SHA256 或 SHA1）解密路径不变，魔数保持 V1/V2，旧 App 仍可读取新备份。
 */
object AuthCrypto {

    private const val MAGIC_V1 = "YUNX_AUTH_V1"
    private const val MAGIC_V2 = "YUNX_AUTH_V2"
    private const val ITERATIONS_V1 = 10_000
    private const val ITERATIONS_V2 = 210_000
    private const val KEY_LENGTH = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val GCM_TAG_BITS = 128

    /** 首选 KDF（API 26+）；API 23-25 不可用 */
    private const val KDF_SHA256 = "PBKDF2WithHmacSHA256"

    /** 兼容 KDF（所有 API 均有）；仅作降级/重试 */
    private const val KDF_SHA1 = "PBKDF2WithHmacSHA1"

    /** 当前首选 KDF：能取到 SHA256 就用 SHA256，否则降级 SHA1（结果缓存一次） */
    private val preferredKdf: String by lazy {
        if (isKdfAvailable(KDF_SHA256)) KDF_SHA256 else KDF_SHA1
    }

    /** 探测算法是否可用（只捕获 NoSuchAlgorithmException，避免掩盖其它错误） */
    private fun isKdfAvailable(algorithm: String): Boolean = try {
        SecretKeyFactory.getInstance(algorithm)
        true
    } catch (_: NoSuchAlgorithmException) {
        false
    }

    /** 加密明文 JSON，返回 Base64 密文（含魔数头部） */
    fun encrypt(plain: String, password: String): String {
        require(password.length >= 8) { "备份口令至少 8 位" }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        // 新备份使用当前设备实际可用的 KDF（API 26+ 为 SHA256，API 23-25 降级 SHA1）
        val key = deriveKey(password, salt, ITERATIONS_V2, preferredKdf)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val magic = MAGIC_V2.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(magic.size + salt.size + iv.size + ciphertext.size)
        System.arraycopy(magic, 0, payload, 0, magic.size)
        System.arraycopy(salt, 0, payload, magic.size, salt.size)
        System.arraycopy(iv, 0, payload, magic.size + salt.size, iv.size)
        System.arraycopy(ciphertext, 0, payload, magic.size + salt.size + iv.size, ciphertext.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /** 解密 Base64 密文；密码错误/文件损坏抛异常 */
    fun decrypt(data: String, password: String): String {
        val payload = Base64.decode(data.trim(), Base64.NO_WRAP)
        val magicV1 = MAGIC_V1.toByteArray(Charsets.UTF_8)
        val magicV2 = MAGIC_V2.toByteArray(Charsets.UTF_8)
        val (magic, iterations) = when {
            payload.copyOfRange(0, minOf(payload.size, magicV2.size)).contentEquals(magicV2) ->
                magicV2 to ITERATIONS_V2
            payload.copyOfRange(0, minOf(payload.size, magicV1.size)).contentEquals(magicV1) ->
                magicV1 to ITERATIONS_V1
            else -> throw IllegalArgumentException("不是有效的加密备份文件")
        }
        require(payload.size >= magic.size + SALT_SIZE + IV_SIZE) { "加密备份文件已损坏" }
        val salt = payload.copyOfRange(magic.size, magic.size + SALT_SIZE)
        val iv = payload.copyOfRange(magic.size + SALT_SIZE, magic.size + SALT_SIZE + IV_SIZE)
        val ciphertext = payload.copyOfRange(magic.size + SALT_SIZE + IV_SIZE, payload.size)

        // 先用当前首选算法解一次；失败再换另一个 KDF 重试一次（兼容另一算法生成的备份）
        val primary = preferredKdf
        val primaryError: Exception = try {
            return decryptWith(ciphertext, password, salt, iv, iterations, primary)
        } catch (e: Exception) {
            e
        }
        val fallback = if (primary == KDF_SHA256) KDF_SHA1 else KDF_SHA256
        if (!isKdfAvailable(fallback)) throw primaryError
        return try {
            decryptWith(ciphertext, password, salt, iv, iterations, fallback)
        } catch (_: Exception) {
            // 两个算法都失败：抛第一次的异常（密码错误 / 文件被篡改）
            throw primaryError
        }
    }

    /** 用指定 KDF 派生密钥并执行 AES-GCM 解密（失败抛 GeneralSecurityException） */
    private fun decryptWith(
        ciphertext: ByteArray,
        password: String,
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int,
        kdf: String
    ): String {
        val key = deriveKey(password, salt, iterations, kdf)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /** 判断内容是否为加密备份（检查魔数头部） */
    fun isEncrypted(data: String): Boolean = runCatching {
        val payload = Base64.decode(data.trim(), Base64.NO_WRAP)
        val magicV1 = MAGIC_V1.toByteArray(Charsets.UTF_8)
        val magicV2 = MAGIC_V2.toByteArray(Charsets.UTF_8)
        (payload.size >= magicV1.size && payload.copyOfRange(0, magicV1.size).contentEquals(magicV1)) ||
            (payload.size >= magicV2.size && payload.copyOfRange(0, magicV2.size).contentEquals(magicV2))
    }.getOrDefault(false)

    /**
     * PBKDF2 派生密钥（口令处理 PBEKeySpec + finally clearPassword 与迭代次数保持原样）：
     * @param kdf 实际使用的 KDF 算法名（默认当前首选；解密重试时显式传入另一个）
     */
    private fun deriveKey(
        password: String,
        salt: ByteArray,
        iterations: Int,
        kdf: String = preferredKdf
    ): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        return try {
            SecretKeyFactory.getInstance(kdf).generateSecret(spec)
        } finally {
            spec.clearPassword()
        }
    }
}
