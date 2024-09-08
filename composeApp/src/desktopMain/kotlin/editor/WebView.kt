package editor

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import com.sun.javafx.application.PlatformImpl
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.StackPane
import javafx.scene.web.WebView
import org.intellij.lang.annotations.Language
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.w3c.dom.events.EventTarget
import org.w3c.dom.html.HTMLAnchorElement
import java.awt.Desktop
import java.net.URI
import javax.swing.UIManager

val LocalWindow = compositionLocalOf<ComposeWindow> { error("No active user found!") }

class FxEventLoopReactivizer(start: Boolean = true) {
    private val listener = object : PlatformImpl.FinishListener {
        // Required to make sure the JavaFx event loop doesn't finish (can happen when java fx panels in app are shown/hidden)
        override fun idle(implicitExit: Boolean) {}
        override fun exitCalled() {}
    }

    fun start() {
        PlatformImpl.addListener(listener)
    }

    init {
        if (start) start()
    }

    fun finish() {
        PlatformImpl.removeListener(listener)
    }
}

@Language("css")
private const val scrollbarCss = """
::-webkit-scrollbar {
  width: 8px; /* Set the width of the vertical scrollbar */
  height: 8px; /* Set the width of the horizontal scrollbar */
}

::-webkit-scrollbar-track {
  background-color: #f1f1f1; /* Set the background color of the track */
}

::-webkit-scrollbar-thumb {
  background-color: #888; /* Set the color of the thumb */
  border-radius: 4px; /* Set the border-radius to make it round */
}

::-webkit-scrollbar-thumb:hover {
  background-color: #555; /* Set the color of the thumb on hover */
}
"""


private val swingPanelColor = UIManager.getColor("Panel.background").let { Color(it.red, it.green, it.blue, it.alpha) }

private fun androidx.compose.ui.graphics.Color.asWebViewBackgroundColor(): String = """
body {
  background-color: rgb(${256 * (red * alpha + swingPanelColor.red * swingPanelColor.alpha * (1 - alpha))}, ${256 * (green * alpha + swingPanelColor.green * swingPanelColor.alpha * (1 - alpha))}, ${256 * (blue * alpha + swingPanelColor.blue * swingPanelColor.alpha * (1 - alpha))});
}
"""

@Composable
fun DesktopWebView(
    modifier: Modifier,
    backgroundColor: androidx.compose.ui.graphics.Color? = null,
    html: String,
) {

    remember { JFXPanel() }
    var webView: WebView? by remember { mutableStateOf(null) }

    var scrollPositions by remember { mutableStateOf(Position(0, 0)) }

    SwingPanel(
        factory = {
            makeWebViewPanel(
                scrollPosition = { scrollPositions },
                backgroundColor = backgroundColor,
                onScrollPosition = { scrollPositions = it },
                onWebView = { webView = it },
            )
        },
        modifier = modifier,
    )

    LaunchedEffect(webView, html, backgroundColor) {
        Platform.runLater {
            webView?.apply {
                engine.loadContent(html, "text/html")
            }
        }
    }
}

fun fakeWebView() {
    JFXPanel()
    makeWebViewPanel {}
}

private data class Position(val vertical: Int, val horizontal: Int)

private fun makeWebViewPanel(scrollPosition: () -> Position? = { null }, backgroundColor: androidx.compose.ui.graphics.Color? = null, onScrollPosition: (Position) -> Unit = { }, onWebView: (WebView) -> Unit): JFXPanel =
    JFXPanel().apply {
        Platform.runLater {
            val webView = WebView().apply {
                isVisible = true
                engine.isJavaScriptEnabled = true
                engine.loadWorker.exceptionProperty().addListener { _, _, newError ->
                    println("Page loading error: $newError")
                }
                engine.loadWorker.stateProperty().addListener { _, _, newState ->
                    if (newState === Worker.State.SUCCEEDED) {
                        val nodeList: NodeList = engine.document.getElementsByTagName("a")
                        for (i in 0 until nodeList.length) {
                            val node: Node = nodeList.item(i)
                            val eventTarget: EventTarget = node as EventTarget
                            eventTarget.addEventListener(
                                "click",
                                { evt ->
                                    val target: EventTarget = evt.currentTarget
                                    val href = (target as HTMLAnchorElement).href
                                    //handle opening URL outside JavaFX WebView
                                    try {
                                        Desktop.getDesktop().browse(URI(href))
                                    } catch (e: Throwable) {
                                        e.printStackTrace()
                                    }
                                    evt.preventDefault()
                                }, false
                            )
                        }
                        
                        scrollPosition()?.let {
                            engine.executeScript("window.scrollTo(${it.horizontal}, ${it.vertical})")
                        }
                    }
                }
                engine.userStyleSheetLocation = "data:text/css;charset=utf-8,$scrollbarCss\n${backgroundColor?.asWebViewBackgroundColor() ?: ""}"
            }
            webView.addEventHandler(ScrollEvent.SCROLL) {
                val vertical = webView.engine.executeScript("document.body.scrollTop") as Int
                val horizontal = webView.engine.executeScript("document.body.scrollLeft") as Int
                onScrollPosition(Position(vertical = vertical, horizontal = horizontal))
            }
            onWebView(webView)
            val root = StackPane()
            root.children.add(webView)
            this.scene = Scene(root)
        }
    }
