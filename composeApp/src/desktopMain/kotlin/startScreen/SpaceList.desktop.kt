package startScreen

import androidx.compose.runtime.*
import androidx.compose.ui.*
import common.EncryptedSpaceInfo
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onExternalSpaces(
    enabled: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    whenDraging: @Composable Modifier.() -> Modifier,
    onSpace: (List<EncryptedSpaceInfo>) -> Unit
): Modifier = composed {
    var isDragging by remember { mutableStateOf(false) }
    (if (isDragging) whenDraging() else this).onExternalDrag(
        enabled = enabled,
        onDragStart = { isDragging = true; onDraggingChange(true) },
        onDragExit = { isDragging = false; onDraggingChange(false) },
        onDrop = { dragValue ->
            isDragging = false
            onDraggingChange(false)
            when (val data = dragValue.dragData) {
                is DragData.FilesList -> {
                    val spaces = data.readFiles().mapNotNull {
                        runCatching {
                            val fileContent = File(URI(it)).readText()
                            fileJson.decodeFromString<List<EncryptedSpaceInfo>>(fileContent)
                        }.getOrNull()
                    }.flatten()
                    onSpace(spaces)
                }
            }
        },
    )
}
