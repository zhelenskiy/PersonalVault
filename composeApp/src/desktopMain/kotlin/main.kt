import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import editor.FxEventLoopReactivizer
import editor.LocalWindow
import editor.fakeWebView

fun main() = application {
    val fxEventLoopReactivizer = remember {
        FxEventLoopReactivizer(start = true)
    }
    Window(
        onCloseRequest = { exitApplication(); fxEventLoopReactivizer.finish() },
        title = "PersonalVault",
    ) {
        CompositionLocalProvider(LocalWindow provides window) {
            App()
        }
    }
    LaunchedEffect(Unit) {
        runCatching {
            fakeWebView() // make initialization in background
        }.onFailure { it.printStackTrace() }
    }
}
