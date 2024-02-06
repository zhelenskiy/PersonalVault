package repositories

import common.EncryptedSpaceInfo
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.StateFlow

interface SpacesRepository {
    val spacesFlow: StateFlow<PersistentList<EncryptedSpaceInfo>>
    
    suspend fun getCurrentSpaces(): PersistentList<EncryptedSpaceInfo>
    fun updateSpaces(newSpacesUpdater: (oldSpaces: PersistentList<EncryptedSpaceInfo>) -> PersistentList<EncryptedSpaceInfo>)
}
