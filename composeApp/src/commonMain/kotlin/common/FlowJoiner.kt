package common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class Versioned<out T>(
    val version: Long?,
    val data: T,
)

suspend fun <T> MutableStateFlow<Versioned<T>>.copyFrom(other: Flow<Versioned<T>>): Unit =
    other.collectLatest(::updateFrom)

fun <T> MutableStateFlow<Versioned<T>>.updateFrom(new: Versioned<T>) {
    while (true) {
        val old = value
        if (new.version == null || old.version != null && old.version >= new.version) break
        if (compareAndSet(old, new)) break
    }
}

suspend fun <T> MutableStateFlow<Versioned<T>>.copyTo(consumer: suspend (Versioned<T>) -> Unit): Unit =
    collectLatest(consumer)

fun <T> MutableStateFlow<Versioned<T>>.syncWith(
    coroutineScope: CoroutineScope,
    values: Flow<Versioned<T>>,
    update: suspend (Versioned<T>) -> Unit,
) {
    coroutineScope.launch { copyFrom(values) }
    coroutineScope.launch { copyTo(update) }
}

fun loadingFlow(coroutineScope: CoroutineScope, vararg flows: StateFlow<Versioned<*>>) = combine(*flows) { values ->
    values.size > 1 && values.any { it.version != values.first().version }
}.stateIn(coroutineScope, SharingStarted.Lazily, false)

inline fun <T : Any> runLogging(block: () -> T): T? = runCatching(block).onFailure { it.printStackTrace() }.getOrNull()

fun <T, R> StateFlow<T>.mapState(coroutineScope: CoroutineScope, transform: (T) -> R): StateFlow<R> =
    map(transform).stateIn(coroutineScope, SharingStarted.WhileSubscribed(), transform(value))

class DataSynchronizer<T>(
    private val coroutineScope: CoroutineScope,
    private val originalFlow: StateFlow<Versioned<T>>,
    private val savedFlow: StateFlow<Versioned<*>>,
    updateOriginalFlow: suspend (Versioned<T>) -> Unit,
    forceResetEventsFlow: SharedFlow<Any> = MutableSharedFlow(),
) {
    private val forceResetEventsFlow = MutableSharedFlow<Any>().apply {
        coroutineScope.launch {
            forceResetEventsFlow.collect { emit(it) }
        }
    }
    private val mutableFlow = MutableStateFlow(originalFlow.value)

    init {
        mutableFlow.syncWith(coroutineScope, originalFlow, updateOriginalFlow)
        coroutineScope.launch {
            this@DataSynchronizer.forceResetEventsFlow.collect {
                mutableFlow.value = originalFlow.value
            }
        }
    }

    val versionedValues: StateFlow<Versioned<T>> get() = mutableFlow
    val values: StateFlow<T> = versionedValues.mapState(coroutineScope) { it.data }
    fun setValue(indexedValue: Versioned<T>) {
        mutableFlow.updateFrom(indexedValue)
    }

    fun forceReset() {
        coroutineScope.launch {
            forceResetEventsFlow.emit(Any())
        }
    }

    val isLoading = loadingFlow(coroutineScope, originalFlow, savedFlow)

    fun <R> map(
        transformForward: suspend (T) -> R,
        initialValue: R,
        transformBackward: suspend T.(R) -> T,
        forceResetEventsFlow: SharedFlow<Any> = MutableSharedFlow(),
    ): DataSynchronizer<R> =
        DataSynchronizer(
            coroutineScope = coroutineScope,
            originalFlow = versionedValues
                .map { (version, value) -> Versioned(version, transformForward(value)) }
                .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), Versioned(null, initialValue)),
            savedFlow = savedFlow,
            updateOriginalFlow = { indexedValue ->
                setValue(Versioned(indexedValue.version, mutableFlow.value.data.transformBackward(indexedValue.data)))
            },
            forceResetEventsFlow = forceResetEventsFlow.combine(this.forceResetEventsFlow) { _, _ -> Any() }
                .shareIn(coroutineScope, SharingStarted.WhileSubscribed())
        )

    fun <R> map(
        transformForward: (T) -> R,
        transformBackward: suspend T.(R) -> T,
        forceResetEventsFlow: SharedFlow<Any> = MutableSharedFlow(),
    ): DataSynchronizer<R> =
        DataSynchronizer(
            coroutineScope = coroutineScope,
            originalFlow = versionedValues.mapState(coroutineScope) { (version, value) ->
                Versioned(version, transformForward(value))
            },
            savedFlow = savedFlow,
            updateOriginalFlow = { indexedValue ->
                setValue(Versioned(indexedValue.version, mutableFlow.value.data.transformBackward(indexedValue.data)))
            },
            forceResetEventsFlow = forceResetEventsFlow.combine(this.forceResetEventsFlow) { _, _ -> Any() }
                .shareIn(coroutineScope, SharingStarted.WhileSubscribed())
        )
}