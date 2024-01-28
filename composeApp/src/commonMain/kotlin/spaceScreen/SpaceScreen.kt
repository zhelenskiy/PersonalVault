package spaceScreen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import common.CardTextField
import common.DeletionConfirmation
import common.ModifiableList
import startScreen.DecryptedSpaceInfo

class SpaceScreen(private val decryptedSpaceInfo: DecryptedSpaceInfo) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        SpaceScreenContent(decryptedSpaceInfo.name, onBackPress = { navigator.pop() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScreenContent(name: String, onBackPress: () -> Unit) {
    val notes: MutableList<Pair<String, String>> = remember { mutableStateListOf() }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(name, modifier = Modifier.horizontalScroll(rememberScrollState())) },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back to space list"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            ModifiableList(
                items = notes,
                onEmptyContent = { Text("No notes yet") },
                onCreateItemRequest = { onClose ->
                    notes.add("" to "")
                    onClose()
                },
                onDeleteItemRequest = { index, (name, note), onClose ->
                    DeletionConfirmation(
                        windowTitle = "Deleting note",
                        text = "Are you sure you want to delete note \"${name}\"?",
                        deleteSpace = { notes.removeAt(index) },
                        closeDialog = onClose,
                    )
                },
                onItemClick = { _, note, onClose ->
                },
            ) { index, (name, note) ->
                CardTextField(name, onValueChange = { notes[index] = it to note })
            }
        }
    }
}
