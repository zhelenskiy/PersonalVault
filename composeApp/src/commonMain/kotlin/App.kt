import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import startScreen.EncryptedSpaceInfo
import startScreen.SpacesScreen
import crypto.*
import startScreen.DecryptedSpaceInfo

@Composable
fun App() {
    MaterialTheme {
        var spaces by remember { mutableStateOf(listOf<EncryptedSpaceInfo>()) }
        val cryptoProvider = remember { CryptoProvider() }
        var currentDecryptedSpaceInfo by remember { mutableStateOf<DecryptedSpaceInfo?>(null) }
        SpacesScreen(
            cryptoProvider = cryptoProvider,
            spaces = spaces,
            onSpacesChange = { spaces = it },
            onSpaceOpen = { currentDecryptedSpaceInfo = it },
        )
        LaunchedEffect(currentDecryptedSpaceInfo) {
            currentDecryptedSpaceInfo?.let { println("Space ${it.name} opened: ${it.decryptedData.decodeToString()}") }
        }
    }
}