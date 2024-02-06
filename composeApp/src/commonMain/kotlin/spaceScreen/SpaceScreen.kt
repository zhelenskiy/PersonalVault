package spaceScreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.kodein.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import common.*
import common.FileSystemItem.FileId
import editor.FileEditorScreen
import kotlinx.collections.immutable.persistentListOf
import crypto.PrivateKey
import kotlinx.collections.immutable.PersistentMap
import kotlin.random.Random

class SpaceScreen(private val index: Int, private val cryptoKey: PrivateKey) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel<_, SpaceScreenModel>(arg = SpaceScreenInfo(index, cryptoKey))
        val spaceStructure by screenModel.spaceFileSystemFlow.collectAsState()
        val name by screenModel.spaceNameFlow.collectAsState()
        SpaceScreenContent(
            name = name,
            onNameChange = screenModel::setName,
            spaceStructure = spaceStructure,
            onSpaceStructureChange = screenModel::setSpaceStructure,
            onBackPress = { navigator.pop() },
            onFileOpen = { navigator += FileEditorScreen(index, cryptoKey, it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScreenContent(
    name: String, onNameChange: (String) -> Unit,
    spaceStructure: SpaceStructure?, onSpaceStructureChange: (SpaceStructure) -> Unit,
    onBackPress: () -> Unit,
    onFileOpen: (FileId) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(IntrinsicSize.Min)) {
                        CardTextField(name, onNameChange)
                    }
                },
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
        if (spaceStructure == null) return@Scaffold
        Box(Modifier.padding(paddingValues).windowInsetsPadding(WindowInsets.ime)) {
            val verticalScroll = rememberScrollState()
            Column(
                modifier = Modifier.verticalScroll(verticalScroll)
            ) {
                BoxWithConstraints(Modifier.animateContentSize()) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = spaceStructure.fileStructure.children.isEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            BasicText("No files or directories")
                        }
                    }
                    FileSystem(
                        this.maxWidth * 0.5f,
                        spaceStructure.fileStructure,
                        onFileOpen = onFileOpen,
                        files = spaceStructure.files,
                        onChange = { root: FileSystemItem.Root, files ->
                            onSpaceStructureChange(SpaceStructure(root, files))
                        }
                    )
                }
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    CreateFileSystemItemButton(spaceStructure.files) { newItem, newFiles ->
                        onSpaceStructureChange(SpaceStructure(FileSystemItem.Root(spaceStructure.fileStructure.children.add(newItem)), newFiles))
                    }
                }
            }
            VerticalScrollbar(verticalScroll)
        }
    }
}

private inline fun <T> ((T, PersistentMap<FileId, File>) -> Unit).id() = this

@Composable
fun FileSystem(
    maxOffset: Dp,
    element: FileSystemItem,
    onFileOpen: (FileId) -> Unit,
    files: PersistentMap<FileId, File>,
    onChange: (FileSystemItem?, PersistentMap<FileId, File>) -> Unit,
): Unit = when (element) {
    is FileSystemItem.RegularFileSystemItem -> FileSystem(
        maxOffset,
        element,
        files = files,
        onFileOpen = onFileOpen,
        onChange = onChange.id<FileSystemItem.RegularFileSystemItem?>()
    )

    is FileSystemItem.Root -> FileSystem(maxOffset, element, onFileOpen, files, onChange.id<FileSystemItem.Root>())
}

@Composable
fun FileSystem(
    maxOffset: Dp,
    element: FileSystemItem.Root,
    onFileOpen: (FileId) -> Unit,
    files: PersistentMap<FileId, File>,
    onChange: (FileSystemItem.Root, PersistentMap<FileId, File>) -> Unit,
): Unit = Column {
    for ((index, subElement) in element.children.withIndex()) {
        FileSystem(
            maxOffset,
            subElement,
            onFileOpen = onFileOpen,
            files = files,
        ) { newSubElement: FileSystemItem.RegularFileSystemItem?, newFiles ->
            val newChildren =
                if (newSubElement != null) element.children.set(index, newSubElement)
                else element.children.removeAt(index)
            onChange(FileSystemItem.Root(newChildren), newFiles)
        }
    }
}


@Composable
fun FileSystem(
    element: FileId,
    offset: Dp = 0.dp,
    onFileOpen: (FileId) -> Unit,
    files: PersistentMap<FileId, File>,
    onChange: (FileId?, PersistentMap<FileId, File>) -> Unit
) {
    val file = files[element] ?: error("File system is corrupted")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onFileOpen(element) },
    ) {
        Spacer(Modifier.width(offset))
        Spacer(Modifier.width(48.dp))
        var changeTypeExpanded by rememberSaveable { mutableStateOf(false) }
        IconButton(onClick = { changeTypeExpanded = true }) {
            file.type.icon()
            DropdownMenu(
                expanded = changeTypeExpanded,
                onDismissRequest = { changeTypeExpanded = false },
            ) {
                for (fileType in FileType.entries) {
                    DropdownMenuItem(
                        text = { Text(fileType.name) },
                        onClick = {
                            onChange(element, files.put(element, File(file.name, fileType, file.content)))
                            changeTypeExpanded = false
                        },
                        leadingIcon = { fileType.icon() }
                    )
                }
            }
        }
        CardTextField(
            file.name,
            onValueChange = { onChange(element, files.put(element, File(it, file.type, file.content))) }
        )
        ModifiableListItemDecoration(
            onDeleteItemRequest = { onClose ->
                DeletionConfirmation(
                    windowTitle = "Deleting file",
                    text = "Are you sure you want to delete file \"${file.name}\"?",
                    deleteSpace = { onChange(null, files.remove(element)) },
                    closeDialog = onClose,
                )
            }
        )
    }
}

@Composable
fun FileSystem(
    maxOffset: Dp,
    element: FileSystemItem.Directory,
    offset: Dp = 0.dp,
    onFileOpen: (FileId) -> Unit,
    files: PersistentMap<FileId, File>,
    onChange: (FileSystemItem.Directory?, PersistentMap<FileId, File>) -> Unit,
): Unit = Column {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(offset))

        val rotation by animateFloatAsState(targetValue = if (element.isCollapsed) -90f else 0f)
        IconButton(onClick = {
            val directory = FileSystemItem.Directory(
                element.name,
                element.children,
                !element.isCollapsed
            )
            onChange(directory, files)
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
            onValueChange = {
                val directory = FileSystemItem.Directory(it, element.children, element.isCollapsed)
                onChange(directory, files)
            }
        )

        CreateFileSystemItemButton(files) { newItem, newFiles ->
            onChange(FileSystemItem.Directory(element.name, element.children.add(newItem), element.isCollapsed), newFiles)
        }

        ModifiableListItemDecoration(
            onDeleteItemRequest = { onClose ->
                DeletionConfirmation(
                    windowTitle = "Deleting folder",
                    text = "Are you sure you want to delete folder \"${element.name}\"?",
                    deleteSpace = {
                        val removedFileIds = buildList {
                            fun traverse(folder: FileSystemItem.Directory) {
                                for (child in folder.children) {
                                    when (child) {
                                        is FileSystemItem.Directory -> traverse(child)
                                        is FileId -> add(child)
                                    }
                                }
                            }
                            traverse(element)
                        }
                        onChange(null, removedFileIds.fold(files, PersistentMap<FileId, File>::remove))
                    },
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
                    files = files,
                    onFileOpen = onFileOpen,
                ) { newSubElement: FileSystemItem.RegularFileSystemItem?, newFiles ->
                    val newChildren =
                        if (newSubElement != null) element.children.set(index, newSubElement)
                        else element.children.removeAt(index)
                    onChange(FileSystemItem.Directory(element.name, newChildren, element.isCollapsed), newFiles)
                }
            }
        }
    }
}

private fun generateFileId(files: PersistentMap<FileId, File>): FileId = generateSequence { FileId(Random.nextLong()) }.first { it !in files }

@Composable
private fun CreateFileSystemItemButton(
    files: PersistentMap<FileId, File>,
    onFileSystemItem: (FileSystemItem.RegularFileSystemItem, PersistentMap<FileId, File>) -> Unit,
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
                    onFileSystemItem(FileSystemItem.Directory("", persistentListOf(), isCollapsed = false), files)
                    dropDownMenuExpanded = false
                }
            )
            for (fileType in FileType.entries) {
                DropdownMenuItem(
                    text = { Text(fileType.name) },
                    onClick = {
                        val fileId = generateFileId(files)
                        onFileSystemItem(fileId, files.put(fileId, File("", fileType)))
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
    onFileOpen: (FileId) -> Unit,
    files: PersistentMap<FileId, File>,
    onChange: (FileSystemItem.RegularFileSystemItem?, PersistentMap<FileId, File>) -> Unit
): Unit = when (element) {
    is FileSystemItem.Directory -> FileSystem(
        maxOffset,
        element,
        offset,
        onFileOpen,
        files,
        onChange.id<FileSystemItem.Directory?>()
    )

    is FileId -> FileSystem(element, offset, onFileOpen, files, onChange)
}
