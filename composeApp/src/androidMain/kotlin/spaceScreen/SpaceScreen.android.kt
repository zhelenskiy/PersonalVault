package spaceScreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.documentfile.provider.DocumentFile
import common.File
import common.FileSystemItem
import common.FileSystemItem.FileId
import common.FileSystemItem.RegularFileSystemItem
import common.SpaceStructure
import io.github.vinceglb.filekit.core.PlatformDirectory
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentList
import repositories.appContext

actual fun Modifier.onExternalFiles(
    mapping: PersistentMap<FileId, File?>,
    enabled: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    whenDraging: @Composable Modifier.() -> Modifier,
    onSpace: (SpaceStructure) -> Unit
): Modifier = this

actual fun PlatformDirectory.toSpaceStructure(mapping: PersistentMap<FileId, File?>): SpaceStructure {
    @Suppress("NAME_SHADOWING")
    var mapping = mapping
    fun impl(file: DocumentFile): RegularFileSystemItem? {
        return when {
            file.isDirectory -> {
                val inner = file.listFiles().mapNotNull(::impl).toPersistentList()
                val id = generateFileId(mapping)
                mapping = mapping.put(id, null)
                FileSystemItem.Directory(id, file.name ?: "", inner)
            }

            file.isFile -> {
                val content = runCatching {
                    appContext.contentResolver.openInputStream(file.uri)?.buffered()?.readAllBytes()
                }.getOrNull() ?: return null
                val baseName = file.name?.substringBeforeLast(".") ?: ""
                val extension = file.name?.let { if ('.' in it) it.substringAfterLast('.') else null }
                val abstractFile = runCatching {
                    File.createFromRealFile(baseName, extension, content)
                }.getOrNull() ?: return null
                val id = generateFileId(mapping)
                mapping = mapping.put(id, abstractFile)
                id
            }

            else -> null
        }
    }

    val content = listOfNotNull(DocumentFile.fromTreeUri(appContext, uri)?.let(::impl)).toPersistentList()
    return SpaceStructure(FileSystemItem.Root(content), mapping)
}
