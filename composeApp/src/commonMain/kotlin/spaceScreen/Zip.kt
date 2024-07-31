package spaceScreen

import common.File
import common.FileSystemItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private suspend fun ZipOutputStream.putRegularFileSystemItem(
    files: Map<FileSystemItem.FileId, File>,
    prefix: String,
    item: FileSystemItem.RegularFileSystemItem
) = when (item) {
    is FileSystemItem.Directory -> putDirectory(files, prefix, item)
    is FileSystemItem.FileId -> files[item]?.let { putFile(prefix, it) }
}

@Suppress("BlockingMethodInNonBlockingContext")
private suspend fun ZipOutputStream.putFile(prefix: String, file: File) {
    val zipEntry = ZipEntry("$prefix${file.name}${file.type.extension?.let { ".$it" } ?: ""}")
    yield()
    putNextEntry(zipEntry)
    write(file.makeFileContent())
    closeEntry()
}

@Suppress("BlockingMethodInNonBlockingContext")
private suspend fun ZipOutputStream.putDirectory(
    files: Map<FileSystemItem.FileId, File>,
    prefix: String,
    directory: FileSystemItem.Directory
) {
    require(directory.name.isNotEmpty()) { "Directory at '$prefix' has empty name" }
    val path = "$prefix${directory.name.removeSuffix("/")}/"
    val zipEntry = ZipEntry(path)
    yield()
    putNextEntry(zipEntry)
    closeEntry()
    for (child in directory.children) {
        putRegularFileSystemItem(files, path, child)
        yield()
    }
}

suspend fun FileSystemItem.Directory.toZipArchive(files: Map<FileSystemItem.FileId, File>): ByteArray =
    withContext(Dispatchers.IO) {
        val byteArrayOutputStream = ByteArrayOutputStream()
        ZipOutputStream(byteArrayOutputStream).use {
            it.putDirectory(files, "", this@toZipArchive)
        }
        byteArrayOutputStream.toByteArray()
    }

suspend fun FileSystemItem.Root.toZipArchive(files: Map<FileSystemItem.FileId, File>): ByteArray =
    withContext(Dispatchers.IO) {
        val byteArrayOutputStream = ByteArrayOutputStream()
        ZipOutputStream(byteArrayOutputStream).use {
            for (child in children) {
                it.putRegularFileSystemItem(files, "", child)
            }
        }
        byteArrayOutputStream.toByteArray()
    }

