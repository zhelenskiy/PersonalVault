package startScreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import common.EncryptedSpaceInfo

actual fun Modifier.onExternalSpaces(
    enabled: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    whenDraging: @Composable Modifier.() -> Modifier,
    onSpace: (List<EncryptedSpaceInfo>) -> Unit
): Modifier = this
