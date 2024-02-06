package repositories

import common.EncryptedSpaceInfo
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class InMemorySpacesRepository: SpacesRepository {
    private val spacesFlowImpl = MutableStateFlow(persistentListOf<EncryptedSpaceInfo>())
    override val spacesFlow: StateFlow<PersistentList<EncryptedSpaceInfo>>
        get() = spacesFlowImpl

    override suspend fun getCurrentSpaces(): PersistentList<EncryptedSpaceInfo> {
        return spacesFlowImpl.value
    }

    override fun updateSpaces(newSpacesUpdater: (oldSpaces: PersistentList<EncryptedSpaceInfo>) -> PersistentList<EncryptedSpaceInfo>) {
        spacesFlowImpl.update(newSpacesUpdater)
    }
}
