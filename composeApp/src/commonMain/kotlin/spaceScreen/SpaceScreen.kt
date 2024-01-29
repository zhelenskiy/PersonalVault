package spaceScreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichText
import common.*
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
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
    var fileSystem by remember { mutableStateOf(FileSystemItem.Root(persistentListOf())) }
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
            val verticalScroll = rememberScrollState()
            Column(
                modifier = Modifier.verticalScroll(verticalScroll)
            ) {
                BoxWithConstraints(Modifier.animateContentSize()) {
                    androidx.compose.animation.AnimatedVisibility(visible = fileSystem.children.isEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            BasicText("No files or directories")
                        }
                    }
                    FileSystem(this.maxWidth * 0.5f, fileSystem, onChange = { it: FileSystemItem.Root -> fileSystem = it })
                }
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    CreateFileSystemItemButton { fileSystem = FileSystemItem.Root(fileSystem.children.add(it)) }
                }

                val reachState = remember { RichTextState() }
                var text by remember { mutableStateOf("") }
                Column {
                    TextField(text, { text = it; reachState.setMarkdown(it) }, Modifier.fillMaxWidth())
                    BasicRichText(reachState)
                }
            }
        }
    }
}

private inline fun <T, R> ((T) -> R).id() = this

@Composable
fun FileSystem(
    maxOffset: Dp,
    element: FileSystemItem,
    onChange: (FileSystemItem?) -> Unit
): Unit = when (element) {
    is FileSystemItem.RegularFileSystemItem -> FileSystem(
        maxOffset, element, onChange = onChange.id<FileSystemItem.RegularFileSystemItem?, _>()
    )

    is FileSystemItem.Root -> FileSystem(maxOffset, element, onChange.id<FileSystemItem.Root, _>())
}

@Composable
fun FileSystem(
    maxOffset: Dp,
    element: FileSystemItem.Root,
    onChange: (FileSystemItem.Root) -> Unit
): Unit = Column {
    for ((index, subElement) in element.children.withIndex()) {
        FileSystem(maxOffset, subElement) { newSubElement: FileSystemItem.RegularFileSystemItem? ->
            val newChildren =
                if (newSubElement != null) element.children.set(index, newSubElement)
                else element.children.removeAt(index)
            onChange(FileSystemItem.Root(newChildren))
        }
    }
}


@Composable
fun FileSystem(
    element: FileSystemItem.File,
    offset: Dp = 0.dp,
    onChange: (FileSystemItem.File?) -> Unit
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.clickable { },
) {
    Spacer(Modifier.width(offset))
    Spacer(Modifier.width(48.dp))
    var changeTypeExpanded by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { changeTypeExpanded = true }) {
        element.type.icon()
        DropdownMenu(
            expanded = changeTypeExpanded,
            onDismissRequest = { changeTypeExpanded = false },
        ) {
            for (fileType in FileType.entries) {
                DropdownMenuItem(
                    text = { Text(fileType.name) },
                    onClick = {
                        onChange(FileSystemItem.File(element.name, fileType, element.content))
                        changeTypeExpanded = false
                    },
                    leadingIcon = { fileType.icon() }
                )
            }
        }
    }
    CardTextField(
        element.name,
        onValueChange = { onChange(FileSystemItem.File(it, element.type, element.content)) }
    )
    ModifiableListItemDecoration(
        onDeleteItemRequest = { onClose ->
            DeletionConfirmation(
                windowTitle = "Deleting file",
                text = "Are you sure you want to delete file \"${element.name}\"?",
                deleteSpace = { onChange(null) },
                closeDialog = onClose,
            )
        }
    )
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun FileType.icon() = when (this) {
    FileType.PlainText -> Icon(Icons.Default.TextSnippet, contentDescription = "Plain text")
    FileType.Markdown -> Icon(painterResource("markdown.xml"), contentDescription = name)
    FileType.Html -> Icon(Icons.Default.Html, contentDescription = name)
}


@Composable
fun FileSystem(
    maxOffset: Dp,
    element: FileSystemItem.Directory,
    offset: Dp = 0.dp,
    onChange: (FileSystemItem.Directory?) -> Unit
): Unit = Column {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        ) {
        Spacer(Modifier.width(offset))

        val rotation by animateFloatAsState(targetValue = if (element.isCollapsed) -90f else 0f)
        IconButton(onClick = {
            onChange(
                FileSystemItem.Directory(
                    element.name,
                    element.children,
                    !element.isCollapsed
                )
            )
        }) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (element.isCollapsed) "Expand directory" else "Collapse directory",
                modifier = Modifier.rotate(rotation)
            )
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder"
            )
        }

        CardTextField(
            element.name,
            onValueChange = { onChange(FileSystemItem.Directory(it, element.children, element.isCollapsed)) }
        )

        CreateFileSystemItemButton {
            onChange(FileSystemItem.Directory(element.name, element.children.add(it), element.isCollapsed))
        }

        ModifiableListItemDecoration(
            onDeleteItemRequest = { onClose ->
                DeletionConfirmation(
                    windowTitle = "Deleting folder",
                    text = "Are you sure you want to delete folder \"${element.name}\"?",
                    deleteSpace = { onChange(null) },
                    closeDialog = onClose,
                )
            }
        )
    }
    AnimatedVisibility(visible = !element.isCollapsed) {
        Column {
            for ((index, subElement) in element.children.withIndex()) {
                FileSystem(
                    maxOffset = maxOffset,
                    element = subElement,
                    offset = minOf(offset + 48.dp, maxOffset),
                ) { newSubElement: FileSystemItem.RegularFileSystemItem? ->
                    val newChildren =
                        if (newSubElement != null) element.children.set(index, newSubElement)
                        else element.children.removeAt(index)
                    onChange(FileSystemItem.Directory(element.name, newChildren, element.isCollapsed))
                }
            }
        }
    }
}

@Composable
private fun CreateFileSystemItemButton(
    onFileSystemItem: (FileSystemItem.RegularFileSystemItem) -> Unit,
) {
    var dropDownMenuExpanded by rememberSaveable { mutableStateOf(false) }
    Column {
        IconButton(onClick = {
            dropDownMenuExpanded = true
        }) {
            Icon(Icons.Default.Add, contentDescription = "New folder")
        }
        DropdownMenu(
            expanded = dropDownMenuExpanded,
            onDismissRequest = { dropDownMenuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Directory") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = "Directory") },
                onClick = {
                    onFileSystemItem(FileSystemItem.Directory("", persistentListOf(), isCollapsed = false))
                    dropDownMenuExpanded = false
                }
            )
            for (fileType in FileType.entries) {
                DropdownMenuItem(
                    text = { Text(fileType.name) },
                    onClick = {
                        onFileSystemItem(FileSystemItem.File("", fileType, byteArrayOf()))
                        dropDownMenuExpanded = false
                    },
                    leadingIcon = { fileType.icon() }
                )
            }
        }
    }
}


@Composable
fun FileSystem(
    maxOffset: Dp,
    element: FileSystemItem.RegularFileSystemItem,
    offset: Dp = 0.dp,
    onChange: (FileSystemItem.RegularFileSystemItem?) -> Unit
): Unit = when (element) {
    is FileSystemItem.Directory -> FileSystem(maxOffset, element, offset, onChange.id<FileSystemItem.Directory?, _>())
    is FileSystemItem.File -> FileSystem(element, offset, onChange.id<FileSystemItem.File?, _>())
}
