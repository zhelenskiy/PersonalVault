package common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun BoxScope.VerticalScrollbar(scrollState: ScrollState) {
    VerticalScrollbar(ScrollbarAdapter(scrollState), modifier = Modifier.align(Alignment.CenterEnd))
}