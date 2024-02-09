import androidx.compose.material3.MaterialTheme
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
import repositories.FileSpacesRepository
import repositories.SpacesRepository
import spaceScreen.spaceModule
import startScreen.*

val rootDI = DI {
    import(spaceListModule)
    import(spaceModule)
    import(fileEditorModule)
    bindSingleton<CryptoProvider> { CryptoProviderImpl() }
    bindSingleton<SpacesRepository> { FileSpacesRepository() }
}

@Composable
fun App() = withDI(rootDI) {
    MaterialTheme {
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