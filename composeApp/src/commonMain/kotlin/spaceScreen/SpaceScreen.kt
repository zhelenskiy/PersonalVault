package spaceScreen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.baseName
import io.github.vinceglb.filekit.core.extension
import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.*
import kotlin.random.Random

class SpaceScreen(private val index: Int, private val cryptoKey: PrivateKey) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel<_, SpaceScreenModel>(arg = SpaceScreenInfo(index, cryptoKey))
        val spaceStructure by screenModel.spaceFileSystemFlow.collectAsState()
        val name by screenModel.spaceNameFlow.collectAsState()
        val isSaving by screenModel.isSaving.collectAsState()
        val blockingFiles = remember { mutableStateMapOf<FileId, Unit>() }
        SpaceScreenContent(
            name = name,
            onNameChange = { screenModel.setName(screenModel.getNewVersionNumber(), it) },
            spaceStructure = spaceStructure,
            onSpaceStructureChange = { screenModel.setSpaceStructure(screenModel.getNewVersionNumber(), it) },
            onBackPress = { navigator.pop() },
            onFileOpen = { navigator += FileEditorScreen(index, cryptoKey, it) },
            isSaving = isSaving,
            blockingFiles = blockingFiles,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SpaceScreenContent(
    name: String, onNameChange: (String) -> Unit,
    spaceStructure: SpaceStructure?, onSpaceStructureChange: (SpaceStructure) -> Unit,
    onBackPress: () -> Unit,
    onFileOpen: (FileId) -> Unit,
    isSaving: Boolean,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(IntrinsicSize.Min)) {
                        CardTextField(name, onNameChange)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPress, enabled = blockingFiles.isEmpty()) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to space list"
                        )
                    }
                },
                actions = {
                    if (isSaving) {
                        SyncIndicator()
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        }
    ) { paddingValues ->
        if (spaceStructure == null) return@Scaffold
        Box(Modifier.padding(paddingValues).windowInsetsPadding(WindowInsets.ime)) {
            val verticalScroll = rememberScrollState()
            Column(
                modifier = Modifier.verticalScroll(verticalScroll)
            ) {
                BoxWithConstraints(Modifier.animateContentHeight()) {
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
                        snackbarHostState = snackbarHostState,
                        blockingFiles = blockingFiles,
                        onChange = { root: FileSystemItem.Root, files ->
                            onSpaceStructureChange(SpaceStructure(root, files))
                        },
                    )
                }
                LazyRow(
                    modifier = Modifier.animateContentWidth().fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (spaceStructure.fileStructure.children.isNotEmpty()) Arrangement.End else Arrangement.SpaceEvenly,
                ) {
                    item {
                        CreateFileSystemItemButton(
                            spaceStructure.files,
                            blockingFiles = blockingFiles,
                            modifier = Modifier.animateItemPlacement(),
                        ) { newItem, newFiles ->
                            onSpaceStructureChange(
                                SpaceStructure(
                                    FileSystemItem.Root(
                                        spaceStructure.fileStructure.children.add(newItem)
                                    ), newFiles
                                )
                            )
                        }
                    }

                    item {
                        FileOpener(snackbarHostState, blockingFiles = blockingFiles, modifier = Modifier.animateItemPlacement()) { files ->
                            val newSpaceStructure = files.fold(spaceStructure) { spaceStructure, file ->
                                val fileId = generateFileId(spaceStructure.files)
                                val children = spaceStructure.fileStructure.children.add(fileId)
                                SpaceStructure(FileSystemItem.Root(children), spaceStructure.files.put(fileId, file))
                            }
                            onSpaceStructureChange(newSpaceStructure)
                        }
                    }

                    if (spaceStructure.fileStructure.children.isNotEmpty()) {
                        item {
                            DirectoryLikeSaver(snackbarHostState, name, blockingFiles = blockingFiles, modifier = Modifier.animateItemPlacement()) {
                                spaceStructure.fileStructure.toZipArchive(spaceStructure.files)
                            }
                        }
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
    snackbarHostState: SnackbarHostState,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    onChange: (FileSystemItem?, PersistentMap<FileId, File>) -> Unit,
): Unit = when (element) {
    is FileSystemItem.RegularFileSystemItem -> FileSystem(
        maxOffset,
        element,
        files = files,
        onFileOpen = onFileOpen,
        snackbarHostState = snackbarHostState,
        blockingFiles = blockingFiles,
        onChange = onChange.id<FileSystemItem.RegularFileSystemItem?>(),
    )

    is FileSystemItem.Root -> FileSystem(
        maxOffset,
        element,
        onFileOpen,
        files,
        snackbarHostState,
        blockingFiles,
        onChange.id<FileSystemItem.Root>()
    )
}

@Composable
fun FileSystem(
    maxOffset: Dp,
    element: FileSystemItem.Root,
    onFileOpen: (FileId) -> Unit,
    files: PersistentMap<FileId, File>,
    snackbarHostState: SnackbarHostState,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    onChange: (FileSystemItem.Root, PersistentMap<FileId, File>) -> Unit,
): Unit = Column {
    for ((index, subElement) in element.children.withIndex()) {
        FileSystem(
            maxOffset,
            subElement,
            onFileOpen = onFileOpen,
            files = files,
            snackbarHostState = snackbarHostState,
            blockingFiles = blockingFiles,
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
    snackbarHostState: SnackbarHostState,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    onChange: (FileId?, PersistentMap<FileId, File>) -> Unit,
) {
    val file = files[element] ?: error("File system is corrupted")
    val coroutineScope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = blockingFiles.isEmpty()) { onFileOpen(element) },
    ) {
        Spacer(Modifier.width(offset))
        Spacer(Modifier.width(48.dp))
        file.type.icon()
        val (rawName, onRawNameChange) = rememberSaveable(file.name, file.type) {
            mutableStateOf("${file.name}.${file.type.extension}")
        }
        val newExtension = rawName.substringAfterLast('.', "")
        var formatChangeWarningVisible by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        CardTextField(rawName, onRawNameChange, modifier = Modifier.focusRequester(focusRequester).onFocusChanged {
            if (!it.hasFocus && newExtension != file.type.extension) formatChangeWarningVisible = true
        })
        LaunchedEffect(rawName) {
            val baseName = rawName.substringBeforeLast('.')
            onChange(element, files.put(element, File(baseName, file.type, file.content)))
        }

        // todo report crash
//        LaunchedEffect(newExtension != file.type.extension) {
//            if (newExtension != file.type.extension) {
//                blockingFiles[element] = Unit
//            } else {
//                blockingFiles -= element
//            }
//        }
        AnimatedVisibility(
            visible = newExtension != file.type.extension,
        ) {
            IconButton(onClick = { formatChangeWarningVisible = true }) {
                Icon(Icons.Default.Warning, "Format change warning")
            }
        }
        if (formatChangeWarningVisible) {
            AlertDialog(
                text = { Text("Do you really want to convert file from .${file.type.extension} to .$newExtension?") },
                onDismissRequest = {
                    formatChangeWarningVisible = false
                },
                confirmButton = {
                    Button(
                        onClick = {
                            try {
                                val converted = file.convert(getFileTypeByExtension(newExtension))
                                onChange(element, files.put(element, converted))
                                formatChangeWarningVisible = false
                            } catch (e: Throwable) {
                                e.printStackTrace()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(e.message ?: "An error occured")
                                }
                            }
                        },
                    ) {
                        Text("Change")
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        onRawNameChange("${rawName.substringBeforeLast('.')}.${file.type.extension}")
                        formatChangeWarningVisible = false
                    }) {
                        Text("Revert")
                    }
                }
            )
        }

        val saveFileLauncher = rememberFileSaverLauncher { file ->
            if (file != null) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("File '${file.name}' was saved")
                }
            }
        }
        IconButton(enabled = blockingFiles.isEmpty(), onClick = {
            saveFileLauncher.launch(
                baseName = file.name,
                bytes = file.makeFileContent(),
                extension = file.type.extension
            )
        }) {
            Icon(Icons.Default.Download, "Download")
        }
        ModifiableListItemDecoration(
            onDeleteItemEnabled = blockingFiles.isEmpty(),
            onDeleteItemRequest = { onClose ->
                DeletionConfirmation(
                    windowTitle = "Deleting file",
                    text = "Do you really want to delete file \"${file.name}\"?",
                    delete = { onChange(null, files.remove(element)) },
                    closeDialog = onClose,
                )
            }
        )
    }
}

@Composable
private fun DirectoryLikeSaver(
    snackbarHostState: SnackbarHostState,
    name: String,
    modifier: Modifier = Modifier,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    toZipArchive: suspend () -> ByteArray
) {
    val coroutineScope = rememberCoroutineScope()
    val saveFileLauncher = rememberFileSaverLauncher { file ->
        if (file != null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("ZIP-archive '${file.name}' was saved")
            }
        }
    }
    IconButton(modifier = modifier, enabled = blockingFiles.isEmpty(), onClick = {
        coroutineScope.launch {
            try {
                val bytes = toZipArchive()
                saveFileLauncher.launch(
                    baseName = name,
                    bytes = bytes,
                    extension = "zip",
                )
            } catch (e: Throwable) {
                snackbarHostState.showSnackbar(e.message ?: "An error occurred")
            }
        }
    }) {
        Icon(Icons.Default.Download, "Download")
    }
}

@Composable
private fun FileOpener(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    onFilesOpen: (List<File>) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val filePickerLauncher = rememberFilePickerLauncher(
        type = PickerType.File(),
        mode = PickerMode.Multiple(),
        title = "Upload files",
    ) { nativeFiles ->
        if (nativeFiles == null) return@rememberFilePickerLauncher
        coroutineScope.launch {
            val files = try {
                nativeFiles.map {
                    val name = it.baseName
                    val bytes = it.readBytes()
                    File.createFromRealFile(name, it.extension, bytes)
                }
            } catch (e: CancellationException) {
                return@launch
            } catch (e: Throwable) {
                snackbarHostState.showSnackbar(e.message ?: "An error occurred")
                return@launch
            }
            onFilesOpen(files)
        }
    }
    IconButton(modifier = modifier, enabled = blockingFiles.isEmpty(), onClick = { filePickerLauncher.launch() }) {
        Icon(Icons.Default.UploadFile, "Upload files")
    }
}

@Composable
fun FileSystem(
    maxOffset: Dp,
    element: FileSystemItem.Directory,
    offset: Dp = 0.dp,
    onFileOpen: (FileId) -> Unit,
    files: PersistentMap<FileId, File>,
    snackbarHostState: SnackbarHostState,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    onChange: (FileSystemItem.Directory?, PersistentMap<FileId, File>) -> Unit,
): Unit = Column {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(offset))

        val rotation by animateFloatAsState(targetValue = if (element.isCollapsed) -90f else 0f)
        IconButton(enabled = blockingFiles.isEmpty(), onClick = {
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

        CreateFileSystemItemButton(files, blockingFiles = blockingFiles) { newItem, newFiles ->
            onChange(
                FileSystemItem.Directory(element.name, element.children.add(newItem), element.isCollapsed),
                newFiles
            )
        }

        FileOpener(snackbarHostState, blockingFiles = blockingFiles) { newFiles ->
            val (newDirectory, newMapping) = newFiles.fold(element to files) { (directory, mapping), file ->
                val fileId = generateFileId(mapping)
                val newDirectory =
                    FileSystemItem.Directory(directory.name, directory.children.add(fileId), directory.isCollapsed)
                val newMapping = mapping.put(fileId, file)
                newDirectory to newMapping
            }
            onChange(newDirectory, newMapping)
        }

        DirectoryLikeSaver(snackbarHostState, element.name, blockingFiles = blockingFiles) {
            element.toZipArchive(files)
        }

        ModifiableListItemDecoration(
//            onDeleteItemEnabled = blockingFiles.isEmpty(),
            onDeleteItemRequest = { onClose ->
                DeletionConfirmation(
                    windowTitle = "Deleting folder",
                    text = "Do you really want to delete folder \"${element.name}\"?",
                    delete = {
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
                    snackbarHostState = snackbarHostState,
                    blockingFiles = blockingFiles,
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

private fun generateFileId(files: PersistentMap<FileId, File>): FileId =
    generateSequence { FileId(Random.nextLong()) }.first { it !in files }

@Composable
private fun CreateFileSystemItemButton(
    files: PersistentMap<FileId, File>,
    modifier: Modifier = Modifier,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    onFileSystemItem: (FileSystemItem.RegularFileSystemItem, PersistentMap<FileId, File>) -> Unit,
) {
    var dropDownMenuExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        IconButton(enabled = blockingFiles.isEmpty(), onClick = {
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
            for (fileType in TextFileType.entries) {
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
    snackbarHostState: SnackbarHostState,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    onChange: (FileSystemItem.RegularFileSystemItem?, PersistentMap<FileId, File>) -> Unit
): Unit = when (element) {
    is FileSystemItem.Directory -> FileSystem(
        maxOffset,
        element,
        offset,
        onFileOpen,
        files,
        snackbarHostState,
        blockingFiles,
        onChange.id<FileSystemItem.Directory?>()
    )

    is FileId -> FileSystem(element, offset, onFileOpen, files, snackbarHostState, blockingFiles, onChange)
}
