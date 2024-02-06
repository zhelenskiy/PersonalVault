import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import webview.FxEventLoopReactivizer
import webview.LocalWindow
import webview.fakeWebView
import kotlin.concurrent.thread

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
            fakeWebView() // make initalization in background
        }.onFailure { it.printStackTrace() }
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}