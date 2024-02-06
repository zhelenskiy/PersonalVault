import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import crypto.*
import editor.fileEditorModule
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.compose.withDI
import repositories.InMemorySpacesRepository
import repositories.SpacesRepository
import spaceScreen.spaceModule
import startScreen.*

val rootDI = DI {
    import(spaceListModule)
    import(spaceModule)
    import(fileEditorModule)
    bindSingleton<CryptoProvider> { CryptoProviderImpl() }
    bindSingleton<SpacesRepository> { InMemorySpacesRepository() }
}

@Composable
fun App() = withDI(rootDI) {
    MaterialTheme {
        Navigator(SpaceListScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}