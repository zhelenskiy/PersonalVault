package spaceScreen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.kodein.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import common.*
import common.FileSystemItem.*
import crypto.PrivateKey
import editor.FileEditorScreen
import io.github.vinceglb.filekit.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.core.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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

@OptIn(ExperimentalMaterial3Api::class)
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

                    ColorSchemeConfigurationButton()
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        }
    ) { paddingValues ->
        if (spaceStructure == null) return@Scaffold
        Box(Modifier.padding(paddingValues).windowInsetsPadding(WindowInsets.ime)) {
            Column(Modifier.fillMaxHeight()) {
                AnimatedVisibility(
                    visible = spaceStructure.fileStructure.children.isEmpty(),
                    enter = fadeIn() + scaleIn() + expandVertically(),
                    exit = fadeOut() + scaleOut() + shrinkVertically(),
                ) {
                    Box(Modifier.fillMaxWidth(), Alignment.Center) {
                        Text("No files or directories")
                    }
                }

                var path by rememberSaveable { mutableStateOf(listOf<Int>()) }
                var firstShownIndex by rememberSaveable { mutableStateOf(0) }
                FileSystem(
                    spaceStructure = spaceStructure,
                    onFileOpen = onFileOpen,
                    snackbarHostState = snackbarHostState,
                    blockingFiles = blockingFiles,
                    path = path,
                    firstShownElementIndex = firstShownIndex,
                    onPathAndFirstShownElementIndexChange = { newPath, newFirstShownIndex ->
                        path = newPath
                        firstShownIndex = newFirstShownIndex
                    },
                    spaceName = name,
                    viewOnly = false,
                    onChange = { root: Root, files ->
                        onSpaceStructureChange(SpaceStructure(root, files))
                    },
                )
            }
        }
    }
}

private inline fun <T> ((T, PersistentMap<FileId, File?>) -> Unit).id() = this

expect fun Modifier.onExternalFiles(
    mapping: PersistentMap<FileId, File?>,
    enabled: Boolean = true,
    onDraggingChange: (Boolean) -> Unit = { },
    whenDraging: @Composable Modifier.() -> Modifier = { this },
    onSpace: (SpaceStructure) -> Unit,
): Modifier


fun moveTo(
    spaceStructure: SpaceStructure,
    oldPath: List<Int>,
    newPath: List<Int>,
    index: Int,
    currentElement: RegularFileSystemItem,
    onMovingToSubdirectory: () -> Unit,
    onChange: (Root, PersistentMap<FileId, File?>) -> Unit,
): Boolean = when {
    oldPath == newPath -> false
    newPath.take(oldPath.size + 1) == oldPath + index -> {
        onMovingToSubdirectory()
        false
    }

    else -> {
        val root = spaceStructure.fileStructure
        // The order is important here, as if we cut, the new path becomes invalid.
        // The moved directory directory is always added to the end, so it does not invaidate anything.
        val shallowCopied =
            changeRootByChangesInsideCurrentFolderImpl(root.children, newPath, 0) { add(currentElement) }
        val cleared = changeRootByChangesInsideCurrentFolderImpl(shallowCopied, oldPath, 0) { removeAt(index) }
        onChange(Root(cleared), spaceStructure.files)
        true
    }
}

fun copyTo(
    spaceStructure: SpaceStructure,
    newPath: List<Int>,
    currentElement: RegularFileSystemItem,
    onChange: (Root, PersistentMap<FileId, File?>) -> Unit
): Boolean {
    val root = spaceStructure.fileStructure
    val oldMapping = spaceStructure.files
    var newMapping = oldMapping

    fun copy(item: RegularFileSystemItem): RegularFileSystemItem? {
        return when (item) {
            is Directory -> {
                val newId = generateFileId(newMapping)
                newMapping = newMapping.put(newId, null)
                Directory(newId, item.name, item.children.mapNotNull(::copy).toPersistentList())
            }

            is FileId -> {
                val file = oldMapping[item] ?: return null
                val newId = generateFileId(newMapping)
                newMapping = newMapping.put(newId, File(file.name, file.type, file.content.copyOf()))
                newId
            }
        }
    }

    val copied = copy(currentElement) ?: return false
    val pasted = changeRootByChangesInsideCurrentFolderImpl(root.children, newPath, 0) { add(copied) }
    onChange(Root(pasted), newMapping)
    return true
}

fun List<java.io.File>.toSpaceStructure(mapping: PersistentMap<FileId, File?>): SpaceStructure {
    @Suppress("NAME_SHADOWING")
    var mapping = mapping
    fun impl(file: java.io.File): RegularFileSystemItem? {
        return when {
            file.isDirectory -> {
                val inner = file.listFiles()?.mapNotNull(::impl)?.toPersistentList() ?: return null
                val id = generateFileId(mapping)
                mapping = mapping.put(id, null)
                Directory(id, file.name, inner)
            }

            file.isFile -> {
                val content = runCatching { file.readBytes() }.getOrNull() ?: return null
                val baseName = file.nameWithoutExtension
                val extension = file.extension
                val abstractFile = runCatching {
                    File.createFromRealFile(baseName, extension.takeIf { '.' in file.name }, content)
                }.getOrNull() ?: return null
                val id = generateFileId(mapping)
                mapping = mapping.put(id, abstractFile)
                id
            }

            else -> null
        }
    }

    val content = mapNotNull(::impl).toPersistentList()
    return SpaceStructure(Root(content), mapping)
}

private fun changeRootByChangesInsideCurrentFolderImpl(
    currentChildren: PersistentList<RegularFileSystemItem>,
    path: List<Int>,
    currentPathIndex: Int,
    change: PersistentList<RegularFileSystemItem>.() -> PersistentList<RegularFileSystemItem>,
): PersistentList<RegularFileSystemItem> {
    if (currentPathIndex == path.size) {
        return currentChildren.change()
    }
    val childIndex = path[currentPathIndex]
    val oldDirectory = currentChildren.getOrNull(childIndex) as? Directory ?: return currentChildren
    val newDirectoryChildren =
        changeRootByChangesInsideCurrentFolderImpl(oldDirectory.children, path, currentPathIndex + 1, change)
    val newDirectory = Directory(oldDirectory.fileId, oldDirectory.name, newDirectoryChildren)
    return currentChildren.set(childIndex, newDirectory)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileSystem(
    spaceStructure: SpaceStructure,
    path: List<Int>,
    firstShownElementIndex: Int,
    onPathAndFirstShownElementIndexChange: (List<Int>, Int) -> Unit,
    onFileOpen: ((FileId) -> Unit)?,
    snackbarHostState: SnackbarHostState,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    spaceName: String,
    viewOnly: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Root, PersistentMap<FileId, File?>) -> Unit,
) {
    var moveRight by remember { mutableStateOf(true) }
    val root = spaceStructure.fileStructure
    val files = spaceStructure.files
    val children = remember(root, path) {
        path.fold(root.children) { children, index ->
            (children.getOrNull(index) as? Directory)?.children ?: return@remember children
        }
    }
    val previousPath = if (path.isEmpty()) null else path.dropLast(1)

    var isLastElementNew by remember { mutableStateOf(false) }

    val changeRootByChangesInsideCurrentFolder = fun (
        newMapping: PersistentMap<FileId, File?>,
        change: PersistentList<RegularFileSystemItem>.() -> PersistentList<RegularFileSystemItem>,
    ) {
        val newChildren = changeRootByChangesInsideCurrentFolderImpl(root.children, path, 0, change)
        val newRoot = Root(newChildren)
        if (root != newRoot || files != newMapping) {
            onChange(newRoot, newMapping)
        }
    }

    var draggingIntoInner by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier.onExternalFiles(
            mapping = files,
            enabled = draggingIntoInner == 0 && !viewOnly,
            whenDraging = { border(2.dp, Color.Blue) }
        ) {
            changeRootByChangesInsideCurrentFolder(it.files) {
                addAll(it.fileStructure.children)
            }
        }
    ) {
        val directoryContentListState = rememberLazyListState()
        LaunchedEffect(path) {
            directoryContentListState.scrollToItem(firstShownElementIndex)
        }
        NavigationRow(
            root = root,
            files = files,
            spaceName = spaceName,
            path = path,
            onPathAndFirstShownElementIndexChange = onPathAndFirstShownElementIndexChange,
            children = children,
            setAnimationLeft = { moveRight = false },
            blockingFiles = blockingFiles,
            snackbarHostState = snackbarHostState,
            viewOnly = viewOnly,
            onAdded = { isNew ->
                coroutineScope.launch {
                    directoryContentListState.animateScrollToItem(children.size)
                    if (isNew) {
                        isLastElementNew = true
                    }
                }
            },
            changeRootByChangesInsideCurrentFolder = { mapping, change ->
                changeRootByChangesInsideCurrentFolder(mapping, change)
            }
        )
        val animationSpec = remember { spring<IntOffset>() }
        val reorderableLazyListState = rememberReorderableLazyListState(directoryContentListState) { from, to ->
            val fromIndex = children.indexOfFirst { it.fileId == from.key }.takeIf { it >= 0 }
                ?: return@rememberReorderableLazyListState
            val toIndex = children.indexOfFirst { it.fileId == to.key }.takeIf { it >= 0 }
                ?: return@rememberReorderableLazyListState
            changeRootByChangesInsideCurrentFolder(files) {
                val element = get(fromIndex)
                removeAt(fromIndex).add(toIndex, element)
            }
        }
        AnimatedContent(
            targetState = path,
            transitionSpec = {
                val slideIn =
                    slideInHorizontally(animationSpec, initialOffsetX = { if (moveRight) it / 5 else -it / 5 })
                val slideOut = slideOutHorizontally(animationSpec, targetOffsetX = { if (moveRight) -it else it })
                slideIn togetherWith slideOut
            },
            modifier = Modifier.weight(1f),
        ) {
            Box(Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = directoryContentListState,
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 1.dp),
                ) {
                    item {
                        Spacer(Modifier.height(1.dp))
                    }
                    if (previousPath != null) {
                        item {
                            ParentDirectory(
                                blockingFiles = blockingFiles,
                                setAnimationLeft = { moveRight = false },
                                onPathChange = { onPathAndFirstShownElementIndexChange(it, path.lastOrNull() ?: 0) },
                                previousPath = previousPath
                            )
                        }
                    }
                    itemsIndexed(children, key = { _, child -> child.fileId }) { index, child ->
                        ReorderableItem(reorderableLazyListState, key = child.fileId) { isDragging ->
                            val moveToShort = fun (newPath: List<Int>) {
                                val success = moveTo(
                                    spaceStructure = spaceStructure, oldPath = path,
                                    newPath = newPath, index = index, currentElement = child,
                                    onMovingToSubdirectory = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Moving a directory to its subdirectory is forbidden!")
                                        }
                                    },
                                    onChange = onChange
                                )
                                if (success) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Moved successfully")
                                    }
                                }
                            }

                            val copyToShort = fun (newPath: List<Int>) {
                                val success = copyTo(spaceStructure, newPath, child, onChange)
                                if (success) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Copied successfully")
                                    }
                                }
                            }

                            val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)

                            when (child) {
                                is Directory -> DirectoryRow(
                                    element = child,
                                    onOpen = {
                                        moveRight = true
                                        onPathAndFirstShownElementIndexChange(path + index, 0)
                                    },
                                    files = files,
                                    snackbarHostState = snackbarHostState,
                                    blockingFiles = blockingFiles,
                                    onDraggingChange = { if (it) draggingIntoInner++ else draggingIntoInner-- },
                                    viewOnly = viewOnly,
                                    spaceName = spaceName,
                                    spaceStructure = spaceStructure,
                                    path = path,
                                    moveTo = moveToShort,
                                    copyTo = copyToShort,
                                    isNewDirectory = index == children.lastIndex && isLastElementNew,
                                    onCreationFinished = { isLastElementNew = false },
                                    modifier = Modifier.draggableHandle(enabled = blockingFiles.isEmpty() && !viewOnly),
                                    elevation = elevation,
                                    onChange = { element, mapping ->
                                        changeRootByChangesInsideCurrentFolder(mapping) {
                                            if (element != null) set(index, element) else removeAt(index)
                                        }
                                    }
                                )

                                is FileId -> FileRow(
                                    element = child,
                                    onFileOpen = onFileOpen,
                                    spaceStructure = spaceStructure,
                                    spaceName = spaceName,
                                    path = path,
                                    snackbarHostState = snackbarHostState,
                                    blockingFiles = blockingFiles,
                                    viewOnly = viewOnly,
                                    moveTo = moveToShort,
                                    copyTo = copyToShort,
                                    isNewFile = index == children.lastIndex && isLastElementNew,
                                    onCreationFinished = { isLastElementNew = false },
                                    modifier = Modifier.draggableHandle(enabled = blockingFiles.isEmpty() && !viewOnly),
                                    elevation = elevation,
                                    onChange = { element, mapping ->
                                        changeRootByChangesInsideCurrentFolder(mapping) {
                                            if (element != null) set(index, element) else removeAt(index)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(directoryContentListState)
            }
        }
    }
}

@Composable
private fun ParentDirectory(
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    setAnimationLeft: () -> Unit,
    onPathChange: (List<Int>) -> Unit,
    previousPath: List<Int>
) {
    Row(
        modifier = Modifier.clickable(enabled = blockingFiles.isEmpty()) {
            setAnimationLeft()
            onPathChange(previousPath)
        }.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Back"
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("..")
    }
}

@Composable
fun NavigationRow(
    root: Root,
    files: PersistentMap<FileId, File?>,
    spaceName: String,
    path: List<Int>,
    onPathAndFirstShownElementIndexChange: (List<Int>, Int) -> Unit,
    children: PersistentList<RegularFileSystemItem>,
    setAnimationLeft: () -> Unit,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    snackbarHostState: SnackbarHostState,
    viewOnly: Boolean,
    onAdded: (isNew: Boolean) -> Unit,
    changeRootByChangesInsideCurrentFolder: (
        newMapping: PersistentMap<FileId, File?>,
        change: PersistentList<RegularFileSystemItem>.() -> PersistentList<RegularFileSystemItem>,
    ) -> Unit
) {
    val names = remember(root, path) {
        buildList {
            var currentChildren = root.children
            add(spaceName)
            for (pathElement in path) {
                val directory = currentChildren.getOrNull(pathElement) as? Directory?
                currentChildren = directory?.children ?: break
                add(directory.name)
            }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var wholeRowWidth by remember { mutableStateOf(0.dp) }
    var pathWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val wideButtons = path.isEmpty() && children.isEmpty()
    var animateContentWidth by remember { mutableStateOf(true) }
    val switchableAnimateContentWidth = fun Modifier.() = if (animateContentWidth) animateContentWidth() else this
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.onSizeChanged { wholeRowWidth = density.run { it.width.toDp() } }.fillMaxWidth(),
    ) {
        val lazyListState = rememberLazyListState(0)
        LaunchedEffect(path) {
            lazyListState.animateScrollToItem(0)
        }
        LaunchedEffect(wholeRowWidth) {
            animateContentWidth = false
            delay(10)
            animateContentWidth = true
        }
        LazyRow(
            modifier = Modifier
                .onSizeChanged { pathWidth = density.run { it.width.toDp() } }
                .switchableAnimateContentWidth()
                .run { if (wideButtons) width(0.dp) else weight(1f) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            state = lazyListState,
            reverseLayout = true,
        ) {
            if (path.isNotEmpty()) {
                itemsIndexed(names.asReversed(), key = { index, _ -> index }) { reversedIndex, pathElement ->
                    val index = names.lastIndex - reversedIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (index > 0) {
                            Text("➤")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = blockingFiles.isEmpty()) {
                                    setAnimationLeft()
                                    val newPath = path.dropLast(names.lastIndex - index)
                                    if (newPath != path) {
                                        onPathAndFirstShownElementIndexChange(newPath, path[newPath.size])
                                    }
                                }
                                .padding(6.dp),
                        ) {
                            if (pathElement.isNotEmpty()) {
                                Text(pathElement)
                            } else {
                                Text("Untitled", color = TextFieldDefaults.colors().disabledTextColor)
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(!viewOnly) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .switchableAnimateContentWidth()
                    .run { if (wideButtons) widthIn(min = wholeRowWidth) else this },
            ) {
                val extraButtonsEnabled = blockingFiles.isEmpty() && !viewOnly

                CreateFileSystemItemButton(
                    files = files,
                    enabled = extraButtonsEnabled,
                    onCreated = { onAdded(true) },
                ) { newItem, newFiles ->
                    changeRootByChangesInsideCurrentFolder(newFiles) { add(newItem) }
                }

                OpenFileSystemItemButton(
                    snackbarHostState = snackbarHostState,
                    files = files,
                    enabled = extraButtonsEnabled,
                    onOpened = { onAdded(false) }
                ) { newItems, newFiles ->
                    changeRootByChangesInsideCurrentFolder(newFiles) { addAll(newItems) }
                }

                DirectoryLikeSaver(
                    snackbarHostState,
                    names.last(),
                    enabled = extraButtonsEnabled,
                    onClose = { file ->
                        if (file != null) {
                            coroutineScope.launch(NonCancellable) {
                                snackbarHostState.showSnackbar("ZIP-archive '${file.name}' was saved")
                            }
                        }
                    }
                ) {
                    Root(children).toZipArchive(files)
                }

                ModifiableListItemDecoration(
                    onDeleteItemEnabled = extraButtonsEnabled && children.isNotEmpty(),
                    deleteIcon = Icons.Default.DeleteSweep,
                    onDeleteItemRequest = { onClose ->
                        DeletionConfirmation(
                            windowTitle = "Deleting folder",
                            text = "Do you really want to clear folder \"${names.last()}\"?",
                            delete = {
                                val removedFileIds = children.flatMap {
                                    when (it) {
                                        is FileId -> listOf(it)
                                        is Directory -> getFileIds(it)
                                    }
                                }
                                val newMapping = removedFileIds.fold(files) { acc, fileId -> acc.remove(fileId) }
                                changeRootByChangesInsideCurrentFolder(newMapping) { clear() }
                            },
                            closeDialog = onClose,
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun FileRow(
    element: FileId,
    onFileOpen: ((FileId) -> Unit)?,
    spaceStructure: SpaceStructure,
    spaceName: String,
    path: List<Int>,
    snackbarHostState: SnackbarHostState,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    viewOnly: Boolean,
    moveTo: (List<Int>) -> Unit,
    copyTo: (List<Int>) -> Unit,
    isNewFile: Boolean = false,
    onCreationFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
    onChange: (FileId?, PersistentMap<FileId, File?>) -> Unit,
) {
    val files = spaceStructure.files
    val file = files[element] ?: error("File system is corrupted")
    val coroutineScope = rememberCoroutineScope()
    Surface(
        modifier = modifier.run {
            if (onFileOpen != null) clickable(enabled = blockingFiles.isEmpty()) { onFileOpen(element) } else this
        },
        shadowElevation = elevation,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                file.type.icon()
            }
            val (rawName, onRawNameChange) = rememberSaveable(file.name, file.type) {
                mutableStateOf(file.type.extension?.let { "${file.name}.$it" } ?: file.name)
            }
            val newExtension = rawName.substringAfterLast('.').takeIf { '.' in rawName }
            var formatChangeWarningVisible by remember { mutableStateOf(false) }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(isNewFile) {
                if (isNewFile) {
                    focusRequester.requestFocus()
                }
            }
            CardTextField(
                rawName,
                onRawNameChange,
                readOnly = viewOnly,
                modifier = Modifier.focusRequester(focusRequester).onFocusChanged {
                    if (!it.hasFocus) {
                        if (newExtension != file.type.extension && !isNewFile) formatChangeWarningVisible = true
                        else if (isNewFile) onCreationFinished()
                    }
                })
            LaunchedEffect(rawName) {
                if (isNewFile) {
                    val baseName = rawName.substringBeforeLast('.')
                    val extension = if ('.' in rawName) rawName.substringAfterLast('.') else null
                    onChange(element, files.put(element, File.invoke(baseName, getFileTypeByExtension(extension))))
                } else {
                    val baseName = rawName.substringBeforeLast('.')
                    if (baseName != file.name) {
                        onChange(element, files.put(element, File(baseName, file.type, file.content)))
                    }
                }
            }

            // crash https://youtrack.jetbrains.com/issue/CMP-5854/Desktop-crash-with-IllegalStateException-Event-cant-be-processed-because-we-do-not-have-an-active-focus-target
            //        LaunchedEffect(newExtension != file.type.extension) {
            //            if (newExtension != file.type.extension) {
            //                blockingFiles[element] = Unit
            //            } else {
            //                blockingFiles -= element
            //            }
            //        }
            AnimatedVisibility(
                visible = newExtension != file.type.extension && !isNewFile,
            ) {
                IconButton(enabled = !isNewFile, onClick = { formatChangeWarningVisible = true }) {
                    Icon(Icons.Default.Warning, "Format change warning")
                }
            }
            if (formatChangeWarningVisible) {
                AlertDialog(
                    text = { Text("Do you really want to convert file from ${file.type.extension?.let { ".$it" } ?: "no extension"} to ${newExtension?.let { ".$it" } ?: "no extension"}?") },
                    onDismissRequest = {
                        formatChangeWarningVisible = false
                    },
                    confirmButton = {
                        Button(
                            enabled = !viewOnly,
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
                        Button(enabled = !viewOnly, onClick = {
                            onRawNameChange("${rawName.substringBeforeLast('.')}${file.type.extension?.let { ".$it" } ?: ""}")
                            formatChangeWarningVisible = false
                        }) {
                            Text("Revert")
                        }
                    }
                )
            }

            AnimatedVisibility(!viewOnly) {
                ExtraActions { hideExtraButtons ->

                    FileRestructuringOperations(
                        enabled = blockingFiles.isEmpty(),
                        snackbarHostState = snackbarHostState,
                        spaceName = spaceName,
                        spaceStructure = spaceStructure,
                        path = path,
                        moveTo = {
                            if (it != null) moveTo(it)
                            hideExtraButtons()
                        },
                        copyTo = {
                            if (it != null) copyTo(it)
                            hideExtraButtons()
                        },
                        copyIcon = Icons.Default.FileCopy,
                    )

                    if (viewOnly) return@ExtraActions
                    val saveFileLauncher = rememberFileSaverLauncher { file ->
                        if (file != null) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("File '${file.name}' was saved")
                            }
                        }
                        hideExtraButtons()
                    }

                    IconButton(enabled = blockingFiles.isEmpty(), onClick = {
                        saveFileLauncher.launch(
                            baseName = file.name,
                            bytes = file.makeFileContent(),
                            extension = file.type.extension ?: ""
                        )
                    }) {
                        Icon(Icons.Default.Download, "Download")
                    }

                    ModifiableListItemDecoration(
                        onDeleteItemEnabled = blockingFiles.isEmpty(),
                        onDeleteItemRequest = { onDeleteDialogClose ->
                            DeletionConfirmation(
                                windowTitle = "Deleting file",
                                text = "Do you really want to delete file \"${file.name}\"?",
                                delete = { onChange(null, files.remove(element)) },
                                closeDialog = { onDeleteDialogClose(); hideExtraButtons() },
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FileRestructuringOperations(
    enabled: Boolean,
    snackbarHostState: SnackbarHostState,
    spaceName: String,
    spaceStructure: SpaceStructure,
    path: List<Int>,
    moveTo: (List<Int>?) -> Unit,
    copyTo: (List<Int>?) -> Unit,
    copyIcon: ImageVector,
) {
    var showMoveDialog by remember { mutableStateOf(false) }
    IconButton(enabled = enabled, onClick = { showMoveDialog = true }) {
        Icon(Icons.AutoMirrored.Filled.DriveFileMove, "Move")
    }
    if (showMoveDialog) {
        LocationChooser("Move to", snackbarHostState, spaceName, spaceStructure, path) {
            showMoveDialog = false
            moveTo(it)
        }
    }

    var showCopyDialog by remember { mutableStateOf(false) }
    IconButton(enabled = enabled, onClick = { showCopyDialog = true }) {
        Icon(copyIcon, "Copy")
    }
    if (showCopyDialog) {
        LocationChooser("Copy to", snackbarHostState, spaceName, spaceStructure, path) {
            showCopyDialog = false
            copyTo(it)
        }
    }
}

@Composable
fun ExtraActions(body: @Composable (hideDialog: () -> Unit) -> Unit) {
    var showExtraActions by remember { mutableStateOf(false) }
    var height by remember { mutableStateOf(0) }
    Box {
        IconButton(
            onClick = { showExtraActions = !showExtraActions },
            modifier = Modifier.onSizeChanged { height = it.height },
        ) {
            Icon(Icons.Default.MoreVert, "More actions")
        }
        if (showExtraActions) {
            Popup(
                onDismissRequest = { showExtraActions = false },
                offset = IntOffset(0, -height),
                alignment = Alignment.BottomCenter,
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        body { showExtraActions = false }
                    }
                }
            }
        }
    }
}

@Composable
fun DirectoryRow(
    element: Directory,
    onOpen: () -> Unit,
    files: PersistentMap<FileId, File?>,
    snackbarHostState: SnackbarHostState,
    blockingFiles: SnapshotStateMap<FileId, Unit>,
    onDraggingChange: (Boolean) -> Unit,
    viewOnly: Boolean,
    spaceName: String,
    spaceStructure: SpaceStructure,
    path: List<Int>,
    moveTo: (List<Int>) -> Unit,
    copyTo: (List<Int>) -> Unit,
    isNewDirectory: Boolean = false,
    onCreationFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
    onChange: (Directory?, PersistentMap<FileId, File?>) -> Unit,
): Unit = Surface(
    modifier = modifier
        .clickable(enabled = blockingFiles.isEmpty()) { onOpen() }
        .onExternalFiles(
            files,
            enabled = !viewOnly,
            onDraggingChange = onDraggingChange,
            whenDraging = { border(2.dp, Color.Blue) }
        ) {
            onChange(Directory(element.fileId, element.name, element.children + it.fileStructure.children), it.files)
        },
    shadowElevation = elevation,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coroutineScope = rememberCoroutineScope()

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder"
            )
        }

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(isNewDirectory) {
            if (isNewDirectory) {
                focusRequester.requestFocus()
                onCreationFinished()
            }
        }

        CardTextField(
            element.name,
            readOnly = viewOnly,
            modifier = Modifier.focusRequester(focusRequester),
            onValueChange = {
                val directory = Directory(element.fileId, it, element.children)
                onChange(directory, files)
            }
        )

        AnimatedVisibility(!viewOnly) {
            ExtraActions { hideExtraButtons ->
                if (viewOnly) return@ExtraActions

                FileRestructuringOperations(
                    enabled = blockingFiles.isEmpty(),
                    snackbarHostState = snackbarHostState,
                    spaceName = spaceName,
                    spaceStructure = spaceStructure,
                    path = path,
                    moveTo = {
                        if (it != null) moveTo(it)
                        hideExtraButtons()
                    },
                    copyTo = {
                        if (it != null) copyTo(it)
                        hideExtraButtons()
                    },
                    copyIcon = Icons.Default.FolderCopy,
                )

                DirectoryLikeSaver(
                    snackbarHostState,
                    element.name,
                    enabled = blockingFiles.isEmpty(),
                    onClose = { file ->
                        if (file != null) {
                            coroutineScope.launch(NonCancellable) {
                                snackbarHostState.showSnackbar("ZIP-archive '${file.name}' was saved")
                            }
                        }
                        hideExtraButtons()
                    }) {
                    element.toZipArchive(files)
                }

                ModifiableListItemDecoration(
                    onDeleteItemEnabled = blockingFiles.isEmpty(),
                    onDeleteItemRequest = { onDeleteDialogClose ->
                        DeletionConfirmation(
                            windowTitle = "Deleting directory",
                            text = "Do you really want to delete directory \"${element.name}\"?",
                            delete = {
                                val removedFileIds = getFileIds(element)
                                onChange(null, removedFileIds.fold(files, PersistentMap<FileId, File?>::remove))
                            },
                            closeDialog = { onDeleteDialogClose(); hideExtraButtons() },
                        )
                    }
                )
            }
        }
    }
}

private fun getFileIds(element: Directory): List<FileId> = buildList {
    fun traverse(folder: Directory) {
        for (child in folder.children) {
            when (child) {
                is Directory -> traverse(child)
                is FileId -> add(child)
            }
        }
    }
    traverse(element)
}

@Composable
private fun DirectoryLikeSaver(
    snackbarHostState: SnackbarHostState,
    name: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClose: (PlatformFile?) -> Unit,
    toZipArchive: suspend () -> ByteArray,
) {
    val coroutineScope = rememberCoroutineScope()
    val saveFileLauncher = rememberFileSaverLauncher { file ->
        onClose(file)
    }
    IconButton(modifier = modifier, enabled = enabled, onClick = {
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
private fun DirectoryOpener(
    snackbarHostState: SnackbarHostState,
    mapping: PersistentMap<FileId, File?>,
    onDirectoryOpen: (SpaceStructure) -> Unit,
    content: @Composable (launchPicker: () -> Unit) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val directoryPickerLauncher = rememberDirectoryPickerLauncher(
        title = "Upload directory",
    ) { platformDirectory ->
        val spaceStructure = platformDirectory?.toSpaceStructure(mapping) ?: return@rememberDirectoryPickerLauncher
        if (spaceStructure.fileStructure.children.isNotEmpty()) {
            onDirectoryOpen(spaceStructure)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Nothing was uploaded")
            }
        }
    }
    content { directoryPickerLauncher.launch() }
}

expect fun PlatformDirectory.toSpaceStructure(mapping: PersistentMap<FileId, File?>): SpaceStructure

@Composable
private fun FileOpener(
    snackbarHostState: SnackbarHostState,
    onFilesOpen: (List<File>) -> Unit,
    content: @Composable (launchPicker: () -> Unit) -> Unit,
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
                nativeFiles.map { file ->
                    val name = file.baseName
                    val bytes = file.readBytes()
                    File.createFromRealFile(name, file.extension.takeIf { '.' in file.name }, bytes)
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
    content { filePickerLauncher.launch() }
}

internal fun generateFileId(files: PersistentMap<FileId, File?>): FileId =
    generateSequence { FileId(Random.nextLong()) }.first { it !in files }

@Composable
private fun CreateFileSystemItemButton(
    files: PersistentMap<FileId, File?>,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onCreated: () -> Unit,
    onFileSystemItem: (RegularFileSystemItem, PersistentMap<FileId, File?>) -> Unit,
) {
    var dropDownMenuExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        IconButton(enabled = enabled, onClick = {
            dropDownMenuExpanded = true
        }) {
            Icon(Icons.Default.Add, contentDescription = "New")
        }
        DropdownMenu(
            expanded = dropDownMenuExpanded,
            onDismissRequest = { dropDownMenuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Directory") },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = "Directory") },
                onClick = {
                    val fileId = generateFileId(files)
                    onFileSystemItem(Directory(fileId, "", persistentListOf()), files.put(fileId, null))
                    dropDownMenuExpanded = false
                    onCreated()
                }
            )
            DropdownMenuItem(
                text = { Text("File") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "File") },
                onClick = {
                    val fileId = generateFileId(files)
                    onFileSystemItem(fileId, files.put(fileId, File("", getFileTypeByExtension(null))))
                    dropDownMenuExpanded = false
                    onCreated()
                }
            )
        }
    }
}

@Composable
private fun OpenFileSystemItemButton(
    snackbarHostState: SnackbarHostState,
    files: PersistentMap<FileId, File?>,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onOpened: () -> Unit,
    onFileSystemItems: (List<RegularFileSystemItem>, PersistentMap<FileId, File?>) -> Unit,
) {
    var dropDownMenuExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        IconButton(enabled = enabled, onClick = {
            dropDownMenuExpanded = true
        }) {
            Icon(Icons.Default.Upload, contentDescription = "Upload")
        }
        DropdownMenu(
            expanded = dropDownMenuExpanded,
            onDismissRequest = { dropDownMenuExpanded = false },
        ) {
            DirectoryOpener(
                snackbarHostState = snackbarHostState,
                mapping = files,
                onDirectoryOpen = {
                    onFileSystemItems(it.fileStructure.children, it.files)
                    dropDownMenuExpanded = false
                    onOpened()
                }
            ) { launchPicker ->
                DropdownMenuItem(
                    text = { Text("Directory") },
                    leadingIcon = { Icon(Icons.Default.DriveFolderUpload, contentDescription = "Directory") },
                    onClick = launchPicker
                )
            }

            FileOpener(
                snackbarHostState = snackbarHostState,
                onFilesOpen = { newFiles ->
                    var newMapping = files
                    val fileIds = mutableListOf<FileId>()
                    for (file in newFiles) {
                        val id = generateFileId(newMapping)
                        newMapping = newMapping.put(id, file)
                        fileIds.add(id)
                    }

                    onFileSystemItems(fileIds, newMapping)
                    dropDownMenuExpanded = false
                    onOpened()
                },
            ) { launchPicker ->
                DropdownMenuItem(
                    text = { Text("File") },
                    leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = "File") },
                    onClick = launchPicker
                )
            }
        }
    }
}

@Composable
fun LocationChooser(
    title: String,
    snackbarHostState: SnackbarHostState,
    spaceName: String,
    spaceStructure: SpaceStructure,
    initialPath: List<Int> = emptyList(),
    onLocationChosen: (List<Int>?) -> Unit,
) {
    var path by rememberSaveable { mutableStateOf(initialPath) }
    var firstShownIndex by rememberSaveable { mutableStateOf(0) }
    AlertDialog(
        title = { Text(title) },
        onDismissRequest = { onLocationChosen(null) },
        confirmButton = {
            Button(onClick = { onLocationChosen(path) }) {
                Text("Choose")
            }
        },
        text = {
            FileSystem(
                spaceStructure = spaceStructure,
                onFileOpen = null,
                snackbarHostState = snackbarHostState,
                blockingFiles = SnapshotStateMap(),
                path = path,
                firstShownElementIndex = firstShownIndex,
                onPathAndFirstShownElementIndexChange = { newPath, newFirstShownIndex ->
                    path = newPath
                    firstShownIndex = newFirstShownIndex
                },
                spaceName = spaceName,
                viewOnly = true,
                modifier = Modifier.fillMaxHeight(0.75f),
                onChange = { _, _ ->
                    try {
                        throw IllegalStateException("Invalid attempt to change tree")
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()
                    }
                },
            )
        },
    )
}
