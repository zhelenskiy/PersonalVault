package startScreen

import cafe.adriel.voyager.core.model.ScreenModel
import common.EncryptedSpaceInfo
import crypto.CryptoProvider
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.bindProvider
import org.kodein.di.instance
import repositories.SpacesRepository

val spaceListModule = DI.Module(name = "SpaceList") {
    bindProvider { SpaceListScreenModel(di) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SpaceListScreenModel(di: DI) : ScreenModel {
    val cryptoProvider by di.instance<CryptoProvider>()
    private val spacesRepository by di.instance<SpacesRepository>()
    private val savingScope get() = spacesRepository.spacesSavingScope

    private val savingCount = MutableStateFlow(0)

    fun setSpaces(spaces: PersistentList<EncryptedSpaceInfo>) {
        spacesImpl.value = spaces
    }

    private fun updateSpacesInRepository(spaces: PersistentList<EncryptedSpaceInfo>) {
        spacesRepository.updateSpaces { spaces }
    }

    private val spacesImpl = MutableStateFlow(persistentListOf<EncryptedSpaceInfo>()).apply {
        savingScope.launch {
            value = spacesRepository.getCurrentSpaces()
            collectLatest {
                try {
                    savingCount.value++
                    updateSpacesInRepository(it)
                } catch (e: Throwable) {
                    e.printStackTrace()
                } finally {
                    savingCount.value--
                }
            }
        }
    }

    val isSaving = savingCount
        .mapLatest { it > 0 }
        .stateIn(savingScope, WhileSubscribed(), false)

    val spaces: StateFlow<PersistentList<EncryptedSpaceInfo>> get() = spacesImpl
}
