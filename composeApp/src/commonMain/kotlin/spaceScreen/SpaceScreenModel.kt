package spaceScreen

import cafe.adriel.voyager.core.model.ScreenModel
import common.EncryptedSpaceInfo
import common.SpaceStructure
import crypto.CryptoProvider
import crypto.PrivateKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import org.kodein.di.DI
import org.kodein.di.bindFactory
import org.kodein.di.instance
import repositories.SpacesRepository
import kotlin.coroutines.EmptyCoroutineContext


val spaceModule = DI.Module("SpaceScreenModel") {
    bindFactory { spaceScreenInfo: SpaceScreenInfo -> SpaceScreenModel(di, spaceScreenInfo) }
}

data class SpaceScreenInfo(
    val spaceIndex: Int,
    val privateKey: PrivateKey,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SpaceScreenModel(di: DI, spaceScreenInfo: SpaceScreenInfo) : ScreenModel {
    private val spaceIndex = spaceScreenInfo.spaceIndex
    private val privateKey = spaceScreenInfo.privateKey
    private val cryptoProvider by di.instance<CryptoProvider>()
    private val spacesRepository by di.instance<SpacesRepository>()

    private val spaceSavingScope = CoroutineScope(EmptyCoroutineContext)

    val spaceNameFlow: StateFlow<String> = spacesRepository.spacesFlow
        .mapLatest { it[spaceIndex].name }
        .stateIn(spaceSavingScope, WhileSubscribed(), "")

    val spaceFileSystemFlow: StateFlow<SpaceStructure?> = spacesRepository.spacesFlow
        .mapLatest { spaces ->
            val space = spaces[spaceIndex]
            SpaceStructure.fromEncryptedBytes(cryptoProvider, space, privateKey)
        }
        .stateIn(spaceSavingScope, WhileSubscribed(), null)

    fun setName(name: String) {
        spacesRepository.updateSpaces {
            val oldSpace = it[spaceIndex]
            val newSpace = EncryptedSpaceInfo(name, oldSpace.publicKey, oldSpace.encryptedData)
            it.set(spaceIndex, newSpace)
        }
    }

    fun setSpaceStructure(newSpaceStructure: SpaceStructure) {
        spacesRepository.updateSpaces {
            val name = it[spaceIndex].name
            val newEncryptedSpace = newSpaceStructure.toEncryptedBytes(name, privateKey, cryptoProvider)
            it.set(spaceIndex, newEncryptedSpace)
        }
    }
}
