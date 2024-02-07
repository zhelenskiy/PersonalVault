package repositories

import common.EncryptedSpaceInfo
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

@RequiresOptIn(message = "It does not contain current updates that are not saved", level = RequiresOptIn.Level.WARNING)
annotation class OutdatedData

interface SpacesRepository {
    @OutdatedData
    val savedSpacesFlow: StateFlow<PersistentList<EncryptedSpaceInfo>>

    suspend fun getCurrentSpaces(): PersistentList<EncryptedSpaceInfo>
    fun updateSpaces(newSpacesUpdater: (oldSpaces: PersistentList<EncryptedSpaceInfo>) -> PersistentList<EncryptedSpaceInfo>)

    val spacesSavingScope: CoroutineScope
}
