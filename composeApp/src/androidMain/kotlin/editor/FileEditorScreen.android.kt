package editor

import android.content.Intent
import android.graphics.Bitmap
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import repositories.appContext
import java.io.ByteArrayInputStream
import java.nio.file.Files
import androidx.compose.ui.graphics.Color as ComposeColor


private data class ScrollPosition(val x: Int, val y: Int)

@Composable
actual fun HtmlView(html: String, backgroundColor: ComposeColor, appended: Boolean, modifier: Modifier) {
    val htmls = remember { MutableStateFlow(html) }
    LaunchedEffect(html) {
        htmls.value = html
    }
    val coroutineScope = rememberCoroutineScope()
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                var pageLoads = 0
                var scrollPosition = ScrollPosition(0, 0)

                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        startActivity(context, Intent(Intent.ACTION_VIEW, request.url), null)
                        return true
                    }

                    override fun onPageCommitVisible(view: WebView, url: String?) {
                        // There is overscroll in Android for some reason, so I can ignore appended flag
                        scrollTo(scrollPosition.x, scrollPosition.y)
                        pageLoads--
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        pageLoads++
                        super.onPageStarted(view, url, favicon)
                    }
                }
                loadData(html, "text/html", "UTF-8")
                settings.useWideViewPort = true
                overScrollMode = WebView.OVER_SCROLL_NEVER

                setOnScrollChangeListener { _, scrollX, scrollY, oldScrollX, oldScrollY ->
                    val delta = 100
                    if (pageLoads != 0 || scrollX == 0 && scrollY == 0 && (oldScrollX > delta || oldScrollY > delta)) {
                        // some flaky bug that scrollX and scrollY are set to 0
                        return@setOnScrollChangeListener
                    }
                    scrollPosition = ScrollPosition(scrollX, scrollY)
                }
                coroutineScope.launch {
                    htmls.collect {
                        pageLoads++
                        loadData(it, "text/html", "UTF-8")
                        pageLoads--
                    }
                }
            }
        },
        modifier = modifier.background(backgroundColor),
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
