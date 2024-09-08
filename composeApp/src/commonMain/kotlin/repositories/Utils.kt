package repositories

import okio.Path
import kotlin.io.path.createDirectories


expect fun pathTo(vararg ids: String): String

fun Path.withCreatedParents() = apply {
    parent?.toNioPath()?.createDirectories()
}
