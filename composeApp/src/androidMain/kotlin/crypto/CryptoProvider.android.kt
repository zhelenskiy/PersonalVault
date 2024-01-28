package crypto

import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.security.SecureRandom

actual class CryptoProviderImpl: CryptoProvider {
    private val secureRandom = SecureRandom()
    actual override fun generateSalt(): ByteArray = secureRandom.generateSeed(64)

    actual override fun generateKeyByteArray(
        config: SCryptConfig,
        password: ByteArray,
        salt: ByteArray,
        length: Int
    ): ByteArray = SCrypt.generate(password, salt, config.n, config.r, config.p, length)
    
    actual override fun sha512(data: ByteArray): ByteArray {
        val digest = SHA512Digest()
        val retValue = ByteArray(digest.digestSize)
        digest.update(data, 0, data.size)
        digest.doFinal(retValue, 0)
        return retValue
    }
    
    private fun cipherData(cipher: PaddedBufferedBlockCipher, data: ByteArray): ByteArray {
        val minSize = cipher.getOutputSize(data.size)
        val outBuf = ByteArray(minSize)
        val length1 = cipher.processBytes(data, 0, data.size, outBuf, 0)
        val length2 = cipher.doFinal(outBuf, length1)
        val actualLength = length1 + length2
        return outBuf.copyOfRange(0, actualLength)
    }

    actual override fun generateInitializationVector(): ByteArray = secureRandom.generateSeed(16)

    actual override fun aes256Encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val aes = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(AESEngine.newInstance()))
        val ivAndKey = ParametersWithIV(KeyParameter(key), iv)
        aes.init(true, ivAndKey)
        return cipherData(aes, data)
    }

    actual override fun aes256Decrypt(encrypted: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val aes = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(AESEngine.newInstance()))
        val ivAndKey = ParametersWithIV(KeyParameter(key), iv)
        aes.init(false, ivAndKey)
        return cipherData(aes, encrypted)
    }
}
