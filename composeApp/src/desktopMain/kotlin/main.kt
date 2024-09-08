import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextAlign.Companion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import editor.FxEventLoopReactivizer
import editor.LocalWindow
import editor.fakeWebView
import it.sauronsoftware.junique.AlreadyLockedException
import it.sauronsoftware.junique.JUnique
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import personalvault.composeapp.generated.resources.Res
import personalvault.composeapp.generated.resources.encrypted
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.coroutines.EmptyCoroutineContext

fun main() {
    val id = "com.zhelenskiy.vault"
    val appearFlow = MutableSharedFlow<Any>()
    val appearFlowScope = CoroutineScope(EmptyCoroutineContext)
    try {
        JUnique.acquireLock(id) {
            appearFlowScope.launch {
                appearFlow.emit(Any())
            }
            null
        }
    } catch (e: AlreadyLockedException) {
        JUnique.sendMessage(id, "")
        JOptionPane.showMessageDialog(null, "That app is already running", "Application Status", JOptionPane.ERROR_MESSAGE)
        return
    }
    application {
        val fxEventLoopReactivizer = remember {
            FxEventLoopReactivizer(start = true)
        }
        Window(
            onCloseRequest = { exitApplication(); fxEventLoopReactivizer.finish() },
            title = "Personal Vault",
            icon = painterResource(Res.drawable.encrypted),
            state = rememberWindowState(width = 900.dp, height = 675.dp)
        ) {
            LaunchedEffect(Unit) {
                if (isMac) {
                    window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                    window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                    window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                }
                appearFlow.collect {
                    SwingUtilities.invokeLater {
                        window.isVisible = true
                        window.isFocusable = true
                        window.requestFocus()
                        window.toFront()
                        window.repaint()
                    }
                }
            }
            CompositionLocalProvider(LocalWindow provides window) {
                App {
                    if (isMac) {
                        Box {
                            Text(
                                text = window.title,
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            runCatching {
                fakeWebView() // make initialization in background
            }.onFailure { it.printStackTrace() }
        }
    }
}

val isMac = System.getProperty("os.name") == "Mac OS X"
