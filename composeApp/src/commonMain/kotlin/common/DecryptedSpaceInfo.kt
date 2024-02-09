package common

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import crypto.*

class DecryptedSpaceInfo(
    val name: String,
    val privateKey: PrivateKey,
    val encryptedData: EncryptedData,
    val decryptedData: ByteArray,
) : JavaSerializable {

    val publicKey get() = privateKey.toPublicKey()

    private val encryptedSpaceInfo = EncryptedSpaceInfo(name, publicKey, encryptedData)

    fun toEncryptedSpaceInfo() = encryptedSpaceInfo

    companion object {
        suspend operator fun invoke(
            name: String,
            privateKey: PrivateKey,
            encryptedData: EncryptedData,
            cryptoProvider: CryptoProvider
        ) = DecryptedSpaceInfo(name, privateKey, encryptedData, cryptoProvider.decryptData(encryptedData, privateKey))

        suspend fun fromDecryptedData(
            name: String,
            privateKey: PrivateKey,
            data: ByteArray,
            provider: CryptoProvider
        ): DecryptedSpaceInfo {
            val encrypted = provider.encryptData(data, privateKey)
            return DecryptedSpaceInfo(name, privateKey, encrypted, data)
        }
    }
}