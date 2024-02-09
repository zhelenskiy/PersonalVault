package startScreen

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import common.*
import crypto.CryptoProvider
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.*
import org.kodein.di.DI
import org.kodein.di.bindProvider
import org.kodein.di.instance
import repositories.OutdatedData
import repositories.SpacesRepository

val spaceListModule = DI.Module(name = "SpaceList") {
    bindProvider { SpaceListScreenModel(di) }
}

class SpaceListScreenModel(di: DI) : ScreenModel {
    val cryptoProvider by di.instance<CryptoProvider>()
    private val spacesRepository by di.instance<SpacesRepository>()
    private val savingScope get() = screenModelScope

    @OptIn(OutdatedData::class)
    private val spacesData = makeSpacesDataSynchronizer(savingScope, spacesRepository)

    fun setSpaces(version: Long, spaces: PersistentList<EncryptedSpaceInfo>) {
        spacesData.setValue(Versioned(version, spaces))
    }

    val isSaving = spacesData.isLoading

    val spaces: StateFlow<PersistentList<EncryptedSpaceInfo>> = spacesData.values

    fun deleteAll() = spacesRepository.reset()

    val isFetchingInitialData = spacesRepository.isFetchingInitialData

    fun getNewVersionNumber() = spacesRepository.getNewVersionNumber()
}
