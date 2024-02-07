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

    private val savingScope get() = spacesRepository.spacesSavingScope

    private val spaceNameFlowImpl = MutableStateFlow("").apply {
        savingScope.launch {
            val spaces = spacesRepository.getCurrentSpaces()
            value = spaces[spaceIndex].name

            collectLatest {
                try {
                    savingCount.value++
                    updateSpaceNameInRepository(it)
                } catch (e: Throwable) {
                    e.printStackTrace()
                } finally {
                    savingCount.value--
                }
            }
        }
    }

    val spaceNameFlow: StateFlow<String> get() = spaceNameFlowImpl
    private val savingCount = MutableStateFlow(0)

    private val spaceFileSystemFlowImpl: MutableStateFlow<SpaceStructure?> = MutableStateFlow<SpaceStructure?>(null).apply {
        savingScope.launch {
            val spaces = spacesRepository.getCurrentSpaces()
            val space = spaces[spaceIndex]
            value = SpaceStructure.fromEncryptedBytes(cryptoProvider, space, privateKey)

            collectLatest { newSpaceStucture ->
                if (newSpaceStucture != null) {
                    try {
                        savingCount.value++
                        updateSpaceInRepository(newSpaceStucture)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    } finally {
                        savingCount.value--
                    }
                }
            }
        }
    }
    val spaceFileSystemFlow: StateFlow<SpaceStructure?> get() = spaceFileSystemFlowImpl

    fun setName(name: String) {
        spaceNameFlowImpl.value = name
    }

    private fun updateSpaceNameInRepository(name: String) {
        spacesRepository.updateSpaces {
            val oldSpace = it[spaceIndex]
            val newSpace = EncryptedSpaceInfo(name, oldSpace.publicKey, oldSpace.encryptedData)
            it.set(spaceIndex, newSpace)
        }
    }

    val isSaving = savingCount
        .mapLatest { it > 0 }
        .stateIn(savingScope, WhileSubscribed(), false)

    fun setSpaceStructure(newSpaceStructure: SpaceStructure) {
        spaceFileSystemFlowImpl.value = newSpaceStructure
    }

    private fun updateSpaceInRepository(newSpaceStructure: SpaceStructure) {
        spacesRepository.updateSpaces {
            val name = it[spaceIndex].name
            val newEncryptedSpace = newSpaceStructure.toEncryptedBytes(name, privateKey, cryptoProvider)
            it.set(spaceIndex, newEncryptedSpace)
        }
    }
}
