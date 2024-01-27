package startScreen

import crypto.CryptoProvider
import crypto.EncryptedData
import crypto.PrivateKey
import crypto.encryptData

class DecryptedSpaceInfo(
    val name: String,
    val privateKey: PrivateKey,
    val encryptedData: EncryptedData,
    val decryptedData: ByteArray,
) {
    val publicKey get() = privateKey.toPublicKey()
    
    private val encryptedSpaceInfo = EncryptedSpaceInfo(name, publicKey, encryptedData)
    
    fun toEncryptedSpaceInfo() = encryptedSpaceInfo
    
    companion object {
        fun fromDecryptedData(name: String, privateKey: PrivateKey, data: ByteArray, provider: CryptoProvider): DecryptedSpaceInfo {
            val encrypted = provider.encryptData(data, privateKey)
            return DecryptedSpaceInfo(name, privateKey, encrypted, data)
        }
    }
}