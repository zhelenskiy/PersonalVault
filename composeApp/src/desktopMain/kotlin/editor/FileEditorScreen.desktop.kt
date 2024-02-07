package editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun HtmlView(html: String, backgroundColor: Color, modifier: Modifier) {
    DesktopWebView(modifier, backgroundColor, html)
}
