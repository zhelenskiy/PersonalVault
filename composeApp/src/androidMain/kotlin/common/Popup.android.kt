package common

import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import startScreen.DialogSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun NativeDialog(title: String, size: DpSize, onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        DialogSurface(content)
    }
}