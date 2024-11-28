package repositories

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem


expect fun pathTo(vararg ids: String): String

fun Path.withCreatedParents() = apply {
    parent?.let(SystemFileSystem::createDirectories)
}
