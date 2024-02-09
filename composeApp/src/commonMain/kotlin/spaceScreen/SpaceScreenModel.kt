package spaceScreen

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import common.*
import crypto.CryptoProvider
import crypto.PrivateKey
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import org.kodein.di.DI
import org.kodein.di.bindFactory
import org.kodein.di.instance
import repositories.OutdatedData
import repositories.SpacesRepository


val spaceModule = DI.Module("SpaceScreenModel") {
    bindFactory { spaceScreenInfo: SpaceScreenInfo -> SpaceScreenModel(di, spaceScreenInfo) }
}

data class SpaceScreenInfo(
    val spaceIndex: Int,
    val privateKey: PrivateKey,
)

class SpaceScreenModel(di: DI, spaceScreenInfo: SpaceScreenInfo) : ScreenModel {
    private val spaceIndex = spaceScreenInfo.spaceIndex
    private val privateKey = spaceScreenInfo.privateKey
    private val cryptoProvider by di.instance<CryptoProvider>()
    private val spacesRepository by di.instance<SpacesRepository>()

    private val savingScope get() = screenModelScope

    @OptIn(OutdatedData::class)
    private val spacesData = makeSpacesDataSynchronizer(savingScope, spacesRepository)

    private val spaceNameData = spacesData.map(
        transformForward = { it[spaceIndex].name },
        transformBackward = {
            val oldSpace = get(spaceIndex)
            val newSpace = EncryptedSpaceInfo(it, oldSpace.publicKey, oldSpace.encryptedData)
            set(spaceIndex, newSpace)
        },
    )

    val spaceNameFlow: StateFlow<String> = spaceNameData.values

    private val spaceStructureData = makeSpaceStructureSyncData(
        spacesData, spaceIndex, privateKey, cryptoProvider
    )

    val spaceFileSystemFlow: StateFlow<SpaceStructure?> = spaceStructureData.values

    fun setName(version: Long, name: String) {
        spaceNameData.setValue(Versioned(version, name))
    }

    val isSaving = combine(spaceNameData.isLoading, spaceStructureData.isLoading, Boolean::or)
        .stateIn(screenModelScope, WhileSubscribed(), spaceNameData.isLoading.value || spaceStructureData.isLoading.value)

    fun setSpaceStructure(version: Long, newSpaceStructure: SpaceStructure) {
        spaceStructureData.setValue(Versioned(version, newSpaceStructure))
    }

    fun getNewVersionNumber() = spacesRepository.getNewVersionNumber()
}
