import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.sun.javafx.application.PlatformImpl
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import org.intellij.lang.annotations.Language
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.EventTarget
import org.w3c.dom.html.HTMLAnchorElement
import webview.FxWebView
import webview.LocalWindow
import java.awt.BorderLayout
import java.awt.Desktop
import java.net.URI
import javax.swing.JPanel

fun main() = application(exitProcessOnExit = true) {
    // Required to make sure the JavaFx event loop doesn't finish (can happen when java fx panels in app are shown/hidden)
    val finishListener = object : PlatformImpl.FinishListener {
        override fun idle(implicitExit: Boolean) {}
        override fun exitCalled() {}
    }
    PlatformImpl.addListener(finishListener)

    Window(
        title = "WebView Test",
        resizable = false,
        state = WindowState(
            placement = WindowPlacement.Floating,
            size = DpSize(800.dp, 600.dp)
        ),
        onCloseRequest = {
            PlatformImpl.removeListener(finishListener)
            exitApplication()
                         },
        content = {
            CompositionLocalProvider(LocalWindow provides this.window) {
                FxWebView(LocalWindow.current, """
                <a href="https://google.com">meow</a>
                """.trimIndent(), modifier = Modifier.fillMaxSize().background(Color.White))
            }
        })
}
