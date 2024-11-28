package spaceScreen

import androidx.compose.runtime.*
import androidx.compose.ui.*
import common.File
import common.FileSystemItem.FileId
import common.SpaceStructure
import common.onExternalJavaFilesDrag
import io.github.vinceglb.filekit.core.PlatformDirectory
import kotlinx.collections.immutable.PersistentMap

actual fun Modifier.onExternalFiles(
    mapping: PersistentMap<FileId, File?>,
    enabled: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    whenDraging: @Composable Modifier.() -> Modifier,
    onSpace: (SpaceStructure) -> Unit,
): Modifier = onExternalJavaFilesDrag(enabled, onDraggingChange, whenDraging) { files ->
    onSpace(files.toSpaceStructure(mapping))
}


actual fun PlatformDirectory.toSpaceStructure(mapping: PersistentMap<FileId, File?>): SpaceStructure = listOf(file).toSpaceStructure(mapping)
