package startScreen

import cafe.adriel.voyager.core.model.ScreenModel
import common.EncryptedSpaceInfo
import crypto.CryptoProvider
import crypto.PrivateKey
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.kodein.di.DI
import org.kodein.di.bindProvider
import org.kodein.di.instance
import repositories.SpacesRepository

val spaceListModule = DI.Module(name = "SpaceList") {
    bindProvider { SpaceListScreenModel(di) }
}

class SpaceListScreenModel(di: DI) : ScreenModel {
    val cryptoProvider by di.instance<CryptoProvider>()
    private val spacesRepository by di.instance<SpacesRepository>()

    fun setSpaces(spaces: PersistentList<EncryptedSpaceInfo>) {
        spacesRepository.updateSpaces { spaces }
    }
    val spaces: StateFlow<PersistentList<EncryptedSpaceInfo>> get() = spacesRepository.spacesFlow
}
