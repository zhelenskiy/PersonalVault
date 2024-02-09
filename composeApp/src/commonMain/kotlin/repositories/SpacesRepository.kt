package repositories

import common.EncryptedSpaceInfo
import common.Versioned
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@RequiresOptIn(message = "It does not contain current updates that are not saved", level = RequiresOptIn.Level.WARNING)
annotation class OutdatedData

val initialValue = Versioned<PersistentList<EncryptedSpaceInfo>>(null, persistentListOf())

interface SpacesRepository {
    @OutdatedData
    val savedSpacesFlow: StateFlow<Versioned<PersistentList<EncryptedSpaceInfo>>?>

    @OutdatedData
    val currentSpaces: StateFlow<Versioned<PersistentList<EncryptedSpaceInfo>>>

    fun updateSpaces(newSpaces: Versioned<PersistentList<EncryptedSpaceInfo>>)

    fun reset()

    val resetEvents: SharedFlow<Any>
    val isFetchingInitialData: StateFlow<Boolean>
    fun getNewVersionNumber(): Long

    fun close()
}
