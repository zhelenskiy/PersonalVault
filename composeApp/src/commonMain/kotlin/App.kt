import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import crypto.*
import editor.fileEditorModule
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.compose.localDI
import org.kodein.di.compose.withDI
import org.kodein.di.instance
import repositories.*
import spaceScreen.spaceModule
import startScreen.*

val rootDI = DI {
    import(spaceListModule)
    import(spaceModule)
    import(fileEditorModule)
    bindSingleton<CryptoProvider> { CryptoProviderImpl() }
    bindSingleton<SpacesRepository> { FileSpacesRepository() }
    bindSingleton<ConfigurationRepository> { FileConfigurationRepository }
}

val staticLightColorScheme = lightColorScheme()
val staticDarkColorScheme = darkColorScheme()

expect val dynamicLightColorScheme: ColorScheme?
    @Composable
    get

expect val dynamicDarkColorScheme: ColorScheme?
    @Composable
    get

private val colorScheme: ColorScheme
    @Composable
    get() {
        val configurationRepository by localDI().instance<ConfigurationRepository>()
        val configuration by configurationRepository.configurationFlow.collectAsState()
        val currentColorSchemeConfiguration = configuration.colorSchemeConfiguration
        val lightColorScheme = dynamicLightColorScheme.takeIf { currentColorSchemeConfiguration.useDynamic } ?: staticLightColorScheme
        val darkColorScheme = dynamicDarkColorScheme.takeIf { currentColorSchemeConfiguration.useDynamic } ?: staticDarkColorScheme
        return when (currentColorSchemeConfiguration.type) {
            ColorSchemeType.Light -> lightColorScheme
            ColorSchemeType.Dark -> darkColorScheme
            ColorSchemeType.System -> if (isSystemInDarkTheme()) darkColorScheme else lightColorScheme
        }
    }

@Composable
fun App() = withDI(rootDI) {
    MaterialTheme(colorScheme = colorScheme) {
        Navigator(SpaceListScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }

    val spacesRepository by localDI().instance<SpacesRepository>()
    DisposableEffect(Unit) {
        onDispose {
            spacesRepository.close()
        }
    }
}