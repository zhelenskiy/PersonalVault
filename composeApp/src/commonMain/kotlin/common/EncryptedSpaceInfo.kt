package common

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import crypto.*

class EncryptedSpaceInfo(
    val name: String,
    val publicKey: PublicKey,
    val encryptedData: EncryptedData,
) : JavaSerializable {
    fun toDecryptedSpaceInfo(provider: CryptoProvider, password: ByteArray): DecryptedSpaceInfo? {
        val privateKey = publicKey.toPrivate(provider, password) ?: return null
        val decryptedData = provider.decryptData(encryptedData, privateKey)
        return DecryptedSpaceInfo(name, privateKey, encryptedData, decryptedData)
    }

    companion object {
        fun fromDecryptedData(
            name: String,
            privateKey: PrivateKey,
            data: ByteArray,
            provider: CryptoProvider
        ): EncryptedSpaceInfo {
            return DecryptedSpaceInfo.fromDecryptedData(name, privateKey, data, provider).toEncryptedSpaceInfo()
        }
    }
}
