package spaceScreen

import androidx.compose.runtime.*
import androidx.compose.ui.*
import common.File
import common.FileSystemItem
import common.SpaceStructure
import io.github.vinceglb.filekit.core.PlatformDirectory
import kotlinx.collections.immutable.PersistentMap
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onExternalFiles(
    mapping: PersistentMap<FileSystemItem.FileId, File>,
    enabled: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    whenDraging: @Composable Modifier.() -> Modifier,
    onSpace: (SpaceStructure) -> Unit,
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
                is DragData.FilesList -> onSpace(data.readFiles().map { java.io.File(URI(it)) }.toSpaceStructure(mapping))
            }
        },
    )
}

actual fun PlatformDirectory.toSpaceStrcuture(mapping: PersistentMap<FileSystemItem.FileId, File>): SpaceStructure = listOf(file).toSpaceStructure(mapping)
