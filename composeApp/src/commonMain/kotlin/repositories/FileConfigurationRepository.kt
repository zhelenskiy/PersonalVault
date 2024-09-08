package repositories

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import kotlin.coroutines.EmptyCoroutineContext

object FileConfigurationRepository : ConfigurationRepository {
    private val kstore: KStore<Configuration> = storeOf(file = pathTo("configuration.json").toPath().withCreatedParents())
    override val configurationFlow: StateFlow<Configuration> = kstore.updates
        .map { it ?: Configuration() }
        .stateIn(configurationSaveScope, SharingStarted.Eagerly, Configuration())

    override fun updateConfiguration(configuration: Configuration) {
        configurationSaveScope.launch {
            kstore.update { configuration }
        }
    }
}

private val configurationSaveScope = CoroutineScope(EmptyCoroutineContext)
