package editor

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.PictureDrawable
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGImageView
import common.File
import repositories.appContext
import java.io.ByteArrayInputStream
import java.nio.file.Files


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
                settings.useWideViewPort = true

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

actual fun openFileInDefaultApp(file: File) {
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.type.extension ?: "") ?: "*/*"
    val ioFile = Files.createTempFile(appContext.filesDir.toPath(), file.name, file.type.extension?.let { ".$it" } ?: "").toFile()
    ioFile.writeBytes(file.makeFileContent())
    val uri = FileProvider.getUriForFile(appContext, "com.zhelenskiy.vault.file-provider", ioFile)
    val intent = Intent(Intent.ACTION_VIEW)
    intent.setDataAndType(uri, mimeType)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        appContext.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ioFile.deleteOnExit()
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}

actual fun ByteArray.toImageBitmap(): ImageBitmap = BitmapFactory.decodeByteArray(this , 0, this.size).asImageBitmap()

@Composable
actual fun SvgImage(byteArray: ByteArray, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            SVGImageView(context).apply {
                val svg = SVG.getFromInputStream(ByteArrayInputStream(byteArray))
                val drawable = PictureDrawable(svg.renderToPicture())
                setImageDrawable(drawable)
            }
        },
        modifier = modifier
    )
}
