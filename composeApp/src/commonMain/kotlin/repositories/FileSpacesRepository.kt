package repositories

import common.*
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.createDirectories

private const val firstCounterValue = Long.MIN_VALUE

class FileSpacesRepository : SpacesRepository {
    private val kStore: KStore<Versioned<List<EncryptedSpaceInfo>>> =
        storeOf(file = pathTo("spaces.json").toPath().also { it.parent?.toNioPath()?.createDirectories() }.also { println("Data: $it") })

    private val spacesSavingScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

    @OutdatedData
    override val savedSpacesFlow: StateFlow<Versioned<PersistentList<EncryptedSpaceInfo>>?> = kStore.updates
        .map { it ?: initialValue }
        .catch { it.printStackTrace() }
        .map { Versioned(version = it.version, data = it.data.toPersistentList()) }
        .stateIn(spacesSavingScope, SharingStarted.Eagerly, null)
 
    @OptIn(OutdatedData::class)
    override val isFetchingInitialData: StateFlow<Boolean> = savedSpacesFlow.mapState(spacesSavingScope) { it == null }

    @OutdatedData
    private val currentSpacesImpl: MutableStateFlow<Versioned<PersistentList<EncryptedSpaceInfo>>> =
        MutableStateFlow(initialValue).apply {
            syncWith(spacesSavingScope, savedSpacesFlow.filterNotNull()) { versionedValue ->
                val saved = savedSpacesFlow.value ?: return@syncWith
                val savedVersion = saved.version
                if (versionedValue.version == null || savedVersion != null && savedVersion >= versionedValue.version) return@syncWith
                kStore.update { versionedValue }
            }
        }

    @OutdatedData
    override val currentSpaces: StateFlow<Versioned<PersistentList<EncryptedSpaceInfo>>> get() = currentSpacesImpl

    @OptIn(OutdatedData::class)
    override fun updateSpaces(newSpaces: Versioned<PersistentList<EncryptedSpaceInfo>>) {
        currentSpacesImpl.updateFrom(newSpaces)
    }

    @OptIn(OutdatedData::class)
    override fun reset() {
        spacesSavingScope.launch {
            currentSpacesImpl.value = initialValue
            kStore.delete()
            resetEventsImpl.emit(Any())
        }
    }
    
    private val resetEventsImpl = MutableSharedFlow<Any>()
    override val resetEvents: SharedFlow<Any> get() = resetEventsImpl

    @OptIn(OutdatedData::class)
    private val versionNumber = MutableStateFlow(firstCounterValue).apply {
        spacesSavingScope.launch {
            val savedVersion = savedSpacesFlow.mapNotNull { it?.version }.first()
            while (true) {
                val current = value
                if (savedVersion < current) break
                if (compareAndSet(current, savedVersion + 1)) break
            }
        }
    }

    override fun getNewVersionNumber() = versionNumber.value++

    override fun close() {
        // set counter to default
        spacesSavingScope.launch {
            kStore.update { it?.copy(version = firstCounterValue) }
        }
    }
}

expect fun pathTo(vararg ids: String): String
