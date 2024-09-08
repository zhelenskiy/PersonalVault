package repositories

import net.harawata.appdirs.AppDirsFactory
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

private val appDirs by lazy {
    AppDirsFactory.getInstance()!!
}

fun getAppDataDir(): String =
    appDirs.getUserDataDir("Secure Vault", "1.0.0", "zhelenskiy")

actual fun pathTo(vararg ids: String): String = (Paths.get(getAppDataDir(), *ids)).absolutePathString()
