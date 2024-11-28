package common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor


@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
internal fun Modifier.onExternalJavaFilesDrag(
    enabled: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    whenDraging: @Composable Modifier.() -> Modifier,
    onFiles: (List<java.io.File>) -> Unit
): Modifier = composed {
    var isDragging by remember { mutableStateOf(false) }
    val dragTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                if (!enabled) return
                println("Enter")
                isDragging = true
                onDraggingChange(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                if (!enabled) return
                println("Exit")
                isDragging = false
                onDraggingChange(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                if (!enabled || !isDragging) return false
                val files =
                    (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)?.filterIsInstance<java.io.File>()
                if (files.isNullOrEmpty()) return false
                onFiles(files)
                isDragging = false
                onDraggingChange(false)
                return true
            }
        }
    }
    (if (isDragging) whenDraging() else this).dragAndDropTarget(
        shouldStartDragAndDrop = { enabled && it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) },
        target = dragTarget,
    )
}
