package crypto

import cafe.adriel.voyager.core.lifecycle.JavaSerializable

/**
 * SCrypt configuration (see https://en.wikipedia.org/wiki/Scrypt).
 */
data class SCryptConfig(
    val n: Int,
    val r: Int,
    val p: Int,
) : JavaSerializable {
    private tailrec fun Int.isPowerOfTwo(): Boolean = when {
        this == 1 -> true
        this > 1 && this % 2 == 0 -> (this / 2).isPowerOfTwo()
        else -> false
    }

    init {
        require(n > 1 && n.isPowerOfTwo()) { "N must be larger than 1 and a power of 2" }
        require(128 * r / 8 >= Int.SIZE_BITS - 1 || n < 1.shl(128 * r / 8)) { "N must be less than 2^(128 * r / 8)" }
        require(r >= 1) { "r - the block size, must be >= 1" }
        require(p > 0 && p <= Int.MAX_VALUE / (128 * r * 8)) { "p - Parallelization parameter, must be a positive integer less than or equal to Integer.MAX_VALUE / (128 * r * 8)" }
    }
}

val defaultSCryptConfig = SCryptConfig(32768, 32, 1)

interface CryptoProvider {
    fun generateSalt(): ByteArray
    fun generateKeyByteArray(config: SCryptConfig, password: ByteArray, salt: ByteArray, length: Int): ByteArray

    fun sha512(data: ByteArray): ByteArray
    fun aes256Encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    fun aes256Decrypt(encrypted: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    fun generateInitializationVector(): ByteArray
}

expect class CryptoProviderImpl(): CryptoProvider {
    override fun generateSalt(): ByteArray
    override fun generateKeyByteArray(config: SCryptConfig, password: ByteArray, salt: ByteArray, length: Int): ByteArray

    override fun sha512(data: ByteArray): ByteArray
    override fun aes256Encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    override fun aes256Decrypt(encrypted: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    override fun generateInitializationVector(): ByteArray
}

class PrivateKey(
    val config: SCryptConfig,
    val key: ByteArray,
    val salt: ByteArray,
    val hash: ByteArray,
): JavaSerializable {
    private val publicKey = PublicKey(config, key.size, salt, hash)
    fun toPublicKey() = publicKey
}

class PublicKey(
    val config: SCryptConfig,
    val keyLength: Int,
    val salt: ByteArray,
    val hash: ByteArray,
): JavaSerializable {
    fun toPrivate(cryptoProvider: CryptoProvider, password: ByteArray): PrivateKey? {
        val keyByteArray =cryptoProvider.generateKeyByteArray(config = config, password = password, salt = salt, length = keyLength)
        val keyHash = cryptoProvider.sha512(salt + keyByteArray)
        if (!keyHash.contentEquals(hash)) return null
        return PrivateKey(config, keyByteArray, salt, keyHash)
    }
}

fun CryptoProvider.generateKey(config: SCryptConfig, password: ByteArray): PrivateKey {
    val salt = generateSalt()
    val aesKeySize = 128 / 8
    val key = generateKeyByteArray(config, password, salt, aesKeySize)
    val hash = sha512(salt + key)
    return PrivateKey(config, key, salt, hash)
}

class EncryptedData(
    val initializationVector: ByteArray,
    val encryptedBytes: ByteArray,
) : JavaSerializable

fun CryptoProvider.encryptData(data: ByteArray, privateKey: PrivateKey): EncryptedData {
    val iv = generateInitializationVector()
    return EncryptedData(iv, aes256Encrypt(data, privateKey.key, iv))
}

fun CryptoProvider.decryptData(encryptedData: EncryptedData, privateKey: PrivateKey): ByteArray {
    return aes256Decrypt(encryptedData.encryptedBytes, privateKey.key, encryptedData.initializationVector)
}
