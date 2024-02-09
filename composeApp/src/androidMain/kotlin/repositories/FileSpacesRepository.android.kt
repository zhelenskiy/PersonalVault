package repositories
import android.content.Context

lateinit var appContext: Context
actual fun pathTo(vararg ids: String): String =
    (listOf(appContext.filesDir.path) + ids).joinToString("/")
