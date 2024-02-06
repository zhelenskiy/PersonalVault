package editor

import android.content.Intent
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.startActivity


private data class ScrollPosition(val x: Float, val y: Float)

@Composable
actual fun HtmlView(html: String, backgroundColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    var scrollPosition by remember { mutableStateOf(ScrollPosition(0f, 0f)) }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        startActivity(context, Intent(Intent.ACTION_VIEW, request.url), null)
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        evaluateJavascript("window.scrollTo(${scrollPosition.x}, ${scrollPosition.y})") {}
                    }
                }
                loadData(html, "text/html", "UTF-8")
                settings.useWideViewPort = false

                setOnScrollChangeListener { _, _, _, _, _ ->
                    evaluateJavascript("document.body.scrollTop") { vertical ->
                        evaluateJavascript("document.documentElement.scrollLeft") { horizontal ->
                            scrollPosition = ScrollPosition(horizontal.toFloat(), vertical.toFloat())
                        }
                    }
                }
            }
        },
        modifier = modifier.background(backgroundColor),
        update = { it.loadData(html, "text/html", "UTF-8") }
    )
}