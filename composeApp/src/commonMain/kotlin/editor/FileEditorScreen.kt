package editor

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import common.CardTextField
import common.FileSystemItem
import common.FileSystemItem.File
import common.TextFileType
import common.icon

class FileEditorScreen(private val file: File) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var file by remember { mutableStateOf(file) }
        FileEditorScreenContent(
            file = file,
            backPress = { navigator.pop() },
            onHomePress = { navigator.popUntilRoot() },
            onFileChanged = { file = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreenContent(
    file: File,
    backPress: () -> Unit,
    onHomePress: () -> Unit,
    onFileChanged: (file: File) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = backPress) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }

                        IconButton(onClick = onHomePress) {
                            Icon(Icons.Default.Home, contentDescription = "To start page")
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(IntrinsicSize.Min)) {
                        file.type.icon()
                        Spacer(Modifier.width(8.dp))
                        CardTextField(file.name) { onFileChanged(File(it, file.type, file.content)) }
                    }
                }
            )
        }
    ) {
        Box(Modifier.padding(it)) {
            when (file) {
                is FileSystemItem.TextFile -> TextFileEditor(file, onFileChanged)
            }
        }
    }
}

@Composable
fun TextFileEditor(file: FileSystemItem.TextFile, onFileChanged: (FileSystemItem.TextFile) -> Unit) = when (file.type) {
    TextFileType.Html -> MarkedUpTextFileEditor(file, RichTextState::setHtml, onFileChanged)
    TextFileType.Markdown -> MarkedUpTextFileEditor(file, RichTextState::setMarkdown, onFileChanged)
    TextFileType.PlainText -> PlainTextFileEditor(file, onFileChanged)
}

@Composable
private fun PlainTextFileEditor(file: FileSystemItem.TextFile, onFileChanged: (FileSystemItem.TextFile) -> Unit) {
    OutlinedTextField(
        value = file.text,
        onValueChange = { onFileChanged(FileSystemItem.TextFile(file.name, file.type, it)) },
        modifier = Modifier.padding(8.dp).fillMaxSize(),
    )
}

private enum class MarkedUpTextFileEditorMode { Source, Both, Preview }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkedUpTextFileEditor(
    file: FileSystemItem.TextFile,
    setState: RichTextState.(String) -> Unit,
    onFileChanged: (FileSystemItem.TextFile) -> Unit
) {
    var mode by remember { mutableStateOf(MarkedUpTextFileEditorMode.Source) }
    Column {
        Row(Modifier.width(IntrinsicSize.Min).align(Alignment.CenterHorizontally)) {
            val chosenColor = MaterialTheme.colorScheme.primary
            val unchosenColor = MaterialTheme.colorScheme.secondary
            Button(
                onClick = { mode = MarkedUpTextFileEditorMode.Source },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == MarkedUpTextFileEditorMode.Source) chosenColor else unchosenColor
                ),
                shape = RoundedCornerShape(50, 0, 0, 50),
            ) {
                Text("Source")
            }

            Spacer(Modifier.width(1.dp))

            Button(
                modifier = Modifier.weight(1f),
                onClick = { mode = MarkedUpTextFileEditorMode.Both },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == MarkedUpTextFileEditorMode.Both) chosenColor else unchosenColor
                ),
                shape = RectangleShape,
            ) {
                Text("Both")
            }

            Spacer(Modifier.width(1.dp))

            Button(
                modifier = Modifier.weight(1f),
                onClick = { mode = MarkedUpTextFileEditorMode.Preview },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == MarkedUpTextFileEditorMode.Preview) chosenColor else unchosenColor
                ),
                shape = RoundedCornerShape(0, 50, 50, 0),
            ) {
                Text("Preview")
            }
        }

        val richTextState = remember(file) {
            RichTextState().apply { setState(file.text) }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val firstWidth by animateDpAsState(
                targetValue = when (mode) {
                    MarkedUpTextFileEditorMode.Source -> maxWidth
                    MarkedUpTextFileEditorMode.Both -> maxWidth / 2
                    MarkedUpTextFileEditorMode.Preview -> 0.dp
                }
            )
            val secondWidth by animateDpAsState(
                when (mode) {
                    MarkedUpTextFileEditorMode.Source -> 0.dp
                    MarkedUpTextFileEditorMode.Both -> maxWidth / 2
                    MarkedUpTextFileEditorMode.Preview -> maxWidth
                }
            )
            Box(
                modifier = Modifier.width(firstWidth).align(Alignment.CenterStart),
            ) {
                androidx.compose.animation.AnimatedVisibility(firstWidth != 0.dp, enter = fadeIn(), exit = fadeOut()) {
                    PlainTextFileEditor(file) {
                        onFileChanged(it)
                    }
                }
            }
            Box(
                modifier = Modifier.width(secondWidth).align(Alignment.CenterEnd)
            ) {
                androidx.compose.animation.AnimatedVisibility(secondWidth != 0.dp, enter = fadeIn(), exit = fadeOut()) {
                    SelectionContainer {
                        RichText(
                            state = richTextState,
                            modifier = Modifier.padding(8.dp).fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
