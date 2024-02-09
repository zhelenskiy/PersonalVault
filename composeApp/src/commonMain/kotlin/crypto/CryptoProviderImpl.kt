package crypto

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable

/**
 * SCrypt configuration (see https://en.wikipedia.org/wiki/Scrypt).
 */
@Serializable
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
    suspend fun generateSalt(): ByteArray
    suspend fun generateKeyByteArray(config: SCryptConfig, password: ByteArray, salt: ByteArray, length: Int): ByteArray

    suspend fun sha512(data: ByteArray): ByteArray
    suspend fun aes256Encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    suspend fun aes256Decrypt(encrypted: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    suspend fun generateInitializationVector(): ByteArray
}

expect class CryptoProviderImpl(): CryptoProvider {
    override suspend fun generateSalt(): ByteArray
    override suspend fun generateKeyByteArray(config: SCryptConfig, password: ByteArray, salt: ByteArray, length: Int): ByteArray

    override suspend fun sha512(data: ByteArray): ByteArray
    override suspend fun aes256Encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    override suspend fun aes256Decrypt(encrypted: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    override suspend fun generateInitializationVector(): ByteArray
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

@Serializable
class PublicKey(
    val config: SCryptConfig,
    val keyLength: Int,
    val salt: ByteArray,
    val hash: ByteArray,
): JavaSerializable {
    suspend fun toPrivate(cryptoProvider: CryptoProvider, password: ByteArray): PrivateKey? {
        val keyByteArray =cryptoProvider.generateKeyByteArray(config = config, password = password, salt = salt, length = keyLength)
        yield()
        val keyHash = cryptoProvider.sha512(salt + keyByteArray)
        yield()
        if (!keyHash.contentEquals(hash)) return null
        yield()
        return PrivateKey(config, keyByteArray, salt, keyHash)
    }
}

suspend fun CryptoProvider.generateKey(config: SCryptConfig, password: ByteArray): PrivateKey {
    val salt = generateSalt()
    yield()
    val aesKeySize = 128 / 8
    val key = generateKeyByteArray(config, password, salt, aesKeySize)
    yield()
    val hash = sha512(salt + key)
    yield()
    return PrivateKey(config, key, salt, hash)
}

@Serializable
class EncryptedData(
    val initializationVector: ByteArray,
    val encryptedBytes: ByteArray,
) : JavaSerializable

suspend fun CryptoProvider.encryptData(data: ByteArray, privateKey: PrivateKey): EncryptedData {
    val iv = generateInitializationVector()
    yield()
    return EncryptedData(iv, aes256Encrypt(data, privateKey.key, iv))
}

suspend fun CryptoProvider.decryptData(encryptedData: EncryptedData, privateKey: PrivateKey): ByteArray {
    return aes256Decrypt(encryptedData.encryptedBytes, privateKey.key, encryptedData.initializationVector)
}
