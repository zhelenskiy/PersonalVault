package common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize

@Composable
expect fun NativeDialog(title: String, size: DpSize, onDismissRequest: () -> Unit, content: @Composable () -> Unit)