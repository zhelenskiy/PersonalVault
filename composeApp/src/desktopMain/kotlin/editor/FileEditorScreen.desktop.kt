package editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import webview.DesktopWebView

@Composable
actual fun HtmlView(html: String, backgroundColor: Color, modifier: Modifier) {
//    WebView(rememberWebViewStateWithHTMLData(html), modifier = modifier)
//    FxWebView(LocalWindow.current, html)
    DesktopWebView(modifier, backgroundColor, html)
//    SwingPanel(
//        factory = {
//            JEditorPane("text/html", html).apply {
//                text = html
//            }
//        },
//        modifier = modifier,
//    )
}
