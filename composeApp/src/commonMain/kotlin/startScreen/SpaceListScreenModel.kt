package startScreen

import cafe.adriel.voyager.core.model.ScreenModel
import crypto.CryptoProvider
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.kodein.di.DI
import org.kodein.di.bindProvider
import org.kodein.di.instance

val spaceListModule = DI.Module(name = "SpaceList") {
    bindProvider { SpaceListScreenModel(di) }
}

class SpaceListScreenModel(di: DI) : ScreenModel {
    val cryptoProvider by di.instance<CryptoProvider>()
    private val spacesImpl: MutableStateFlow<PersistentList<EncryptedSpaceInfo>> = MutableStateFlow(persistentListOf())
    fun setSpaces(spaces: PersistentList<EncryptedSpaceInfo>) {
        this.spacesImpl.value = spaces
    }
    val spaces: StateFlow<PersistentList<EncryptedSpaceInfo>> get() = spacesImpl
}
