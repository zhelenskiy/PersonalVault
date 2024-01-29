package common

import kotlinx.collections.immutable.PersistentList

enum class FileType {
    PlainText,
    Markdown,
    Html,
}

sealed class FileSystemItem {
    sealed class RegularFileSystemItem: FileSystemItem() {
        abstract val name: String
    }
    class File(override val name: String, val type: FileType, val content: ByteArray) : RegularFileSystemItem()
    class Directory(override val name: String, val children: PersistentList<RegularFileSystemItem>, val isCollapsed: Boolean) : RegularFileSystemItem()
    
    class Root(val children: PersistentList<RegularFileSystemItem>) : FileSystemItem()
}