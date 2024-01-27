package common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import startScreen.DialogSurface

@Composable
actual fun NativeDialog(title: String, size: DpSize, onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    DialogWindow(
        title = title,
        state = DialogState(width = size.width, height = size.height),
        onCloseRequest = onDismissRequest,
        resizable = false,
    ) {
        DialogSurface(content)
    }
}
