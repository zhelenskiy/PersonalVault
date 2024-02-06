package webview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.round
import com.sun.javafx.application.PlatformImpl
import com.sun.javafx.webkit.Accessor
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.event.EventType
import javafx.scene.Scene
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.StackPane
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import org.intellij.lang.annotations.Language
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.w3c.dom.events.EventTarget
import org.w3c.dom.html.HTMLAnchorElement
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Desktop
import java.net.URI
import javax.swing.JPanel

val LocalWindow = compositionLocalOf<ComposeWindow> { error("No active user found!") }

@Composable
fun FxWebView(window: ComposeWindow, @Language("html") html: String, modifier: Modifier = Modifier) {
    val jfxPanel = remember { JFXPanel() }
    var jsObject = remember<JSObject?> { null }

    Box(modifier = modifier) {

        ComposeJFXPanel(
            composeWindow = window,
            jfxPanel = jfxPanel,
            onCreate = {
                Platform.runLater {
                    val root = WebView()
                    val engine = root.engine
                    val scene = Scene(root)
                    engine.loadWorker.stateProperty().addListener { _, _, newState ->
                        if (newState === Worker.State.SUCCEEDED) {
                            jsObject = root.engine.executeScript("window") as JSObject
                            // execute other javascript / setup js callbacks fields etc..
                        }
                    }
                    engine.loadWorker.exceptionProperty().addListener { _, _, newError ->
                        println("page load error : $newError")
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
                        }
                    }
                    jfxPanel.scene = scene
                    engine.loadContent(html, "text/html")
                    engine.setOnError { error -> println("onError : $error") }
                }
            }, onDestroy = {
                Platform.runLater {
                    jsObject?.let { jsObj ->
                        // clean up code for more complex implementations i.e. removing javascript callbacks etc..
                    }
                }
            })
    }
}

@Composable
fun ComposeJFXPanel(
    composeWindow: ComposeWindow,
    jfxPanel: JFXPanel,
    onCreate: () -> Unit,
    onDestroy: () -> Unit = {}
) {
    val jPanel = remember { JPanel() }
    val density = LocalDensity.current.density

    Layout(
        content = {},
        modifier = Modifier.onGloballyPositioned { childCoordinates ->
            val coordinates = childCoordinates.parentCoordinates!!
            val location = coordinates.localToWindow(Offset.Zero).round()
            val size = coordinates.size
            jPanel.setBounds(
                (location.x / density).toInt(),
                (location.y / density).toInt(),
                (size.width / density).toInt(),
                (size.height / density).toInt()
            )
            jPanel.validate()
            jPanel.repaint()
        },
        measurePolicy = { _, _ -> layout(0, 0) {} })

    DisposableEffect(jPanel) {
        composeWindow.add(jPanel)
        jPanel.layout = BorderLayout(0, 0)
        jPanel.add(jfxPanel)
        onCreate()
        onDispose {
            onDestroy()
            composeWindow.remove(jPanel)
        }
    }
}

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

private fun androidx.compose.ui.graphics.Color.asWebViewBackgroundColor(): String = """
body {
  background-color: rgb(${red * 256}, ${green * 256}, ${blue * 256});
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
                    println("page load error : $newError")
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
                onScrollPosition(Position(vertical = vertical, horizontal = horizontal).also(::println))
            }
            onWebView(webView)
            val root = StackPane()
            root.children.add(webView)
            this.scene = Scene(root)
        }
    }
