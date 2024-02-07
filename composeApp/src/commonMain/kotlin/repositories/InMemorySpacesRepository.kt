package repositories

import common.EncryptedSpaceInfo
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.EmptyCoroutineContext

class InMemorySpacesRepository: SpacesRepository {
    private val spacesFlowImpl = MutableStateFlow(persistentListOf<EncryptedSpaceInfo>())
    @OutdatedData
    override val savedSpacesFlow: StateFlow<PersistentList<EncryptedSpaceInfo>>
        get() = spacesFlowImpl

    override suspend fun getCurrentSpaces(): PersistentList<EncryptedSpaceInfo> {
        return spacesFlowImpl.value
    }

    override fun updateSpaces(newSpacesUpdater: (oldSpaces: PersistentList<EncryptedSpaceInfo>) -> PersistentList<EncryptedSpaceInfo>) {
        spacesFlowImpl.update(newSpacesUpdater)
    }
    override val spacesSavingScope = CoroutineScope(EmptyCoroutineContext)
}
