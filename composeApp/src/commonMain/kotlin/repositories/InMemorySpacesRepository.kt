package repositories

import common.EncryptedSpaceInfo
import common.Versioned
import common.updateFrom
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

class InMemorySpacesRepository : SpacesRepository {
    private val spacesFlowImpl = MutableStateFlow(Versioned(null, persistentListOf<EncryptedSpaceInfo>()))

    @OutdatedData
    override val savedSpacesFlow: StateFlow<Versioned<PersistentList<EncryptedSpaceInfo>>>
        get() = spacesFlowImpl
    private val spacesSavingScope = CoroutineScope(EmptyCoroutineContext)

    @OutdatedData
    override val currentSpaces: StateFlow<Versioned<PersistentList<EncryptedSpaceInfo>>>
        get() = savedSpacesFlow

    override fun updateSpaces(newSpaces: Versioned<PersistentList<EncryptedSpaceInfo>>) {
        spacesFlowImpl.updateFrom(newSpaces)
    }

    override fun reset() {
        spacesFlowImpl.value = Versioned(null, persistentListOf())
        spacesSavingScope.launch {
            resetEventsImpl.emit(Any())
        }
    }

    private val resetEventsImpl = MutableSharedFlow<Any>()

    override val resetEvents: SharedFlow<Any>
        get() = resetEventsImpl

    override val isFetchingInitialData: StateFlow<Boolean> = MutableStateFlow(false)
    private val versionNumber = MutableStateFlow(Long.MIN_VALUE)
    override fun getNewVersionNumber() = versionNumber.value++
    override fun close() {}
}
