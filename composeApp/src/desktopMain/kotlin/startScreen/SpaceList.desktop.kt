package startScreen

import androidx.compose.runtime.*
import androidx.compose.ui.*
import common.EncryptedSpaceInfo
import common.onExternalJavaFilesDrag
import java.io.File
import java.net.URI

actual fun Modifier.onExternalSpaces(
    enabled: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    whenDraging: @Composable Modifier.() -> Modifier,
    onSpaces: (List<EncryptedSpaceInfo>) -> Unit
): Modifier = onExternalJavaFilesDrag(enabled, onDraggingChange, whenDraging) { files ->
    val encryptedSpaceInfoList = files.mapNotNull {
        runCatching {
            val fileContent = it.readText()
            fileJson.decodeFromString<List<EncryptedSpaceInfo>>(fileContent)
        }.getOrNull()
    }.flatten()
    onSpaces(encryptedSpaceInfoList)
}
