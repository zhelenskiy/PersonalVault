package repositories

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object InMemoryConfigurationRepository : ConfigurationRepository {
    private val mutableFlow = MutableStateFlow(Configuration())
    override val configurationFlow: StateFlow<Configuration>
        get() = mutableFlow

    override fun updateConfiguration(configuration: Configuration) {
        mutableFlow.value = configuration
    }
}
