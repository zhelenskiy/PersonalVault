package common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.PersistentList
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

sealed class FileType {
    val name = this::class.simpleName!!

    companion object {
        val entries: List<FileType> = TextFileType.entries
    }
}

sealed class TextFileType : FileType() {
    data object PlainText : TextFileType() {

    }

    data object Markdown : TextFileType()
    data object Html : TextFileType()
    companion object {
        val entries = listOf(PlainText, Markdown, Html)
    }
}

sealed class FileSystemItem {
    sealed class RegularFileSystemItem : FileSystemItem() {
        abstract val name: String
    }

    sealed class File : RegularFileSystemItem() {
        abstract val type: FileType
        abstract val content: ByteArray
        companion object {
            operator fun invoke(name: String, type: FileType, content: ByteArray): File = when (type) {
                is TextFileType -> TextFile(name, type, content.decodeToString())
            }
        }
    }

    class TextFile(override val name: String, override val type: TextFileType, val text: String) : File() {
        override val content: ByteArray = text.encodeToByteArray()
    }

    class Directory(
        override val name: String,
        val children: PersistentList<RegularFileSystemItem>,
        val isCollapsed: Boolean
    ) : RegularFileSystemItem()

    class Root(val children: PersistentList<RegularFileSystemItem>) : FileSystemItem()
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun FileType.icon() = when (this) {
    TextFileType.PlainText -> Icon(Icons.Default.TextSnippet, contentDescription = "Plain text")
    TextFileType.Markdown -> Icon(painterResource("markdown.xml"), contentDescription = name)
    TextFileType.Html -> Icon(Icons.Default.Html, contentDescription = name)
}