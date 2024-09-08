package repositories

import kotlinx.serialization.Serializable

@Serializable
data class Configuration(val colorSchemeConfiguration: ColorSchemeConfiguration = ColorSchemeConfiguration())

@Serializable
enum class ColorSchemeType {
    Light, Dark, System
}

@Serializable
data class ColorSchemeConfiguration(
    val type: ColorSchemeType = ColorSchemeType.System,
    val useDynamic: Boolean = true,
)

interface ConfigurationRepository {
    val configurationFlow: kotlinx.coroutines.flow.StateFlow<Configuration>
    fun updateConfiguration(configuration: Configuration)
}
