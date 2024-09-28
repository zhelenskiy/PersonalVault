import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

private val currentColorSchemeConfiguration: ColorSchemeConfiguration
    @Composable
    get() {
        val configurationRepository by localDI().instance<ConfigurationRepository>()
        val configuration by configurationRepository.configurationFlow.collectAsState()
        return configuration.colorSchemeConfiguration
    }

val isDarkTheme: Boolean
    @Composable
    get() = when (currentColorSchemeConfiguration.type) {
        ColorSchemeType.Light -> false
        ColorSchemeType.Dark -> true
        ColorSchemeType.System -> isSystemInDarkTheme()
    }

private val colorScheme: ColorScheme
    @Composable
    get() {
        val lightColorScheme = dynamicLightColorScheme.takeIf { currentColorSchemeConfiguration.useDynamic } ?: staticLightColorScheme
        val darkColorScheme = dynamicDarkColorScheme.takeIf { currentColorSchemeConfiguration.useDynamic } ?: staticDarkColorScheme
        return if (isDarkTheme) darkColorScheme else lightColorScheme
    }

@Composable
fun App(title: @Composable () -> Unit = {}) = withDI(rootDI) {
    MaterialTheme(colorScheme = colorScheme) {
        Column {
            title()
            Navigator(SpaceListScreen()) { navigator ->
                SlideTransition(navigator)
            }
        }
    }

    val spacesRepository by localDI().instance<SpacesRepository>()
    DisposableEffect(Unit) {
        onDispose {
            spacesRepository.close()
        }
    }
}