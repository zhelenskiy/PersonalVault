package editor

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import common.File
import java.awt.Desktop
import java.io.ByteArrayInputStream
import kotlin.io.path.createTempFile

@Composable
actual fun HtmlView(html: String, backgroundColor: Color, appended: Boolean, modifier: Modifier) {
    DesktopWebView(modifier, backgroundColor, html, appended)
}

actual fun openFileInDefaultApp(file: File) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        val ioFile = createTempFile(suffix = file.type.extension?.let { ".$it" } ?: "").toFile()
        ioFile.writeBytes(file.makeFileContent())
        Desktop.getDesktop().open(ioFile)
        ioFile.deleteOnExit()
    }
}

actual fun ByteArray.toImageBitmap(): ImageBitmap = loadImageBitmap(ByteArrayInputStream(this))

@Composable
actual fun SvgImage(byteArray: ByteArray, modifier: Modifier) {
    Image(loadSvgPainter(ByteArrayInputStream(byteArray), LocalDensity.current), null, modifier = modifier)
}
