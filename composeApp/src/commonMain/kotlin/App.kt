import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import crypto.*
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.compose.withDI
import startScreen.*

val rootDI = DI {
    import(spaceListModule)
    bindSingleton<CryptoProvider> { CryptoProviderImpl() }
}

@Composable
fun App() = withDI(rootDI) {
    MaterialTheme {
        Navigator(SpaceListScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}