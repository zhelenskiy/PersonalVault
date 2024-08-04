package startScreen

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.kodein.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import common.*
import crypto.*
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.collections.immutable.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import spaceScreen.SpaceScreen
import kotlin.random.Random

class SpaceListScreen : Screen {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel<SpaceListScreenModel>()
        val versionedSpaces by screenModel.spaces.collectAsState()
        val spaces = versionedSpaces
        val navigator = LocalNavigator.currentOrThrow
        val isSaving by screenModel.isSaving.collectAsState()
        val isFetchingInitialData by screenModel.isFetchingInitialData.collectAsState()
        SpaceListScreenContent(
            cryptoProvider = screenModel.cryptoProvider,
            spaces = spaces,
            onSpacesChange = { screenModel.setSpaces(screenModel.getNewVersionNumber(), it) },
            onSpaceOpen = { index, privateKey -> navigator += SpaceScreen(index, privateKey) },
            isSaving = isSaving,
            onDeleteAll = { screenModel.deleteAll() },
            isFetchingInitialData = isFetchingInitialData,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceListScreenContent(
    cryptoProvider: CryptoProvider,
    spaces: PersistentList<EncryptedSpaceInfo>,
    onSpacesChange: (list: PersistentList<EncryptedSpaceInfo>) -> Unit,
    onSpaceOpen: (index: Int, privateKey: PrivateKey) -> Unit,
    onDeleteAll: () -> Unit,
    isSaving: Boolean,
    isFetchingInitialData: Boolean,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Spaces",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    if (isSaving) {
                        SyncIndicator()
                    }

                    AnimatedVisibility(spaces.isNotEmpty()) {
                        var show by remember { mutableStateOf(false) }
                        IconButton(onClick = { show = !show }) {
                            Icon(Icons.Default.Download, "Export all")
                        }
                        if (show) {
                            SpacesExport(
                                spaces = spaces,
                                reportMessage = { coroutineScope.launch { snackbarHostState.showSnackbar(it) } },
                                onDone = { show = false },
                            )
                        }
                    }

                    var showDeleteAllDialog by rememberSaveable { mutableStateOf(false) }
                    IconButton(onClick = { showDeleteAllDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, "Delete all")
                    }
                    if (showDeleteAllDialog) {
                        DeletionConfirmation(
                            windowTitle = "Deleting space",
                            text = "Do you really want to delete all spaces?",
                            delete = onDeleteAll,
                            closeDialog = { showDeleteAllDialog = false },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        var spacesToIds by remember {
            mutableStateOf(persistentMapOf<EncryptedSpaceInfo, Long>())
        }
        var idsToSpaces by remember {
            mutableStateOf(persistentMapOf<Long, EncryptedSpaceInfo>())
        }
        LaunchedEffect(spaces) {
            val spacesToDelete = spacesToIds.keys - spaces
            idsToSpaces = idsToSpaces.mutate {
                for (spaceToDelete in spacesToDelete) {
                    it.remove(spacesToIds[spaceToDelete])
                }
            }
            spacesToIds -= spacesToDelete
        }
        fun getOrPutId(space: EncryptedSpaceInfo): Long {
            spacesToIds[space]?.let { return it }
            val id = generateSequence { Random.nextLong() }.dropWhile { it in spacesToIds.values }.first()
            spacesToIds = spacesToIds.put(space, id)
            idsToSpaces = idsToSpaces.put(id, space)
            return id
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.ime).padding(paddingValues)) {
            AnimatedVisibility(isFetchingInitialData, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Retrieving saved data",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(20.dp))
                    CircularProgressIndicator()
                }
            }
            val lazyListState: LazyListState = rememberLazyListState()
            AnimatedVisibility(!isFetchingInitialData, enter = fadeIn(), exit = fadeOut()) {
                ModifiableList(
                    items = spaces,
                    onEmptyContent = { Text("No spaces yet") },
                    onCreateItemRequest = { onClose ->
                        NewSpaceDialog(
                            cryptoProvider = cryptoProvider,
                            onDismissRequest = onClose,
                            addSpace = { onSpacesChange(spaces.add(it)) }
                        )
                    },
                    onChangeItemRequest = { index, space, onClose ->
                        EditSpaceDialog(
                            cryptoProvider = cryptoProvider,
                            oldSpaceInfo = space,
                            onDismissRequest = onClose,
                            replaceSpace = { newSpace ->
                                onSpacesChange(spaces.set(index, newSpace))
                            }
                        )
                    },
                    onDeleteItemRequest = { index, space, onClose ->
                        DeletionConfirmation(
                            windowTitle = "Deleting space",
                            text = "Do you really want to delete space \"${space.name}\"?",
                            delete = { onSpacesChange(spaces.removeAt(index)) },
                            closeDialog = onClose,
                        )
                    },
                    onItemClick = { index, space, onClose ->
                        OpenSpaceDialog(
                            cryptoProvider = cryptoProvider,
                            spaceInfo = space,
                            onDismissRequest = onClose,
                            onPrivateKeyReceived = { onSpaceOpen(index, it) },
                        )
                    },
                    onItemExport = { _, space, onEnd ->
                        SpacesExport(
                            spaces = listOf(space),
                            reportMessage = { coroutineScope.launch { snackbarHostState.showSnackbar(it) } },
                            onDone = onEnd,
                        )
                    },
                    onItemsUpload = { onEnd ->
                        SpacesImport(
                            reportMessage = { coroutineScope.launch { snackbarHostState.showSnackbar(it) } },
                            onSpaces = { files ->
                                if (files.isNullOrEmpty()) {
                                    onEnd(false)
                                } else {
                                    onSpacesChange(spaces.addAll(files))
                                    onEnd(true)
                                }
                            }
                        )
                    },
                    key = { getOrPutId(it) },
                    indexByKey = { spaces.indexOf(idsToSpaces[it]) },
                    changeOrder = { from, to ->
                        val element = spaces[from]
                        onSpacesChange(spaces.removeAt(from).add(to, element))
                    },
                    lazyListState = lazyListState,
                    modifier = Modifier.onExternalSpaces(
                        whenDraging = { border(2.dp, Color.Blue) },
                    ) { newSpaces ->
                        if (newSpaces.isNotEmpty()) {
                            onSpacesChange(spaces.addAll(newSpaces))
                            coroutineScope.launch {
                                launch {
                                    lazyListState.animateScrollToItem(Int.MAX_VALUE)
                                }
                                snackbarHostState.showSnackbar("Imported ${newSpaces.size} space${if (newSpaces.size == 1) "" else "s"} successfully")
                            }
                        }
                    },
                ) { index, space ->
                    CardTextField(
                        value = space.name,
                        onValueChange = {
                            val encryptedSpaceInfo = EncryptedSpaceInfo(it, space.publicKey, space.encryptedData)
                            onSpacesChange(spaces.set(index = index, element = encryptedSpaceInfo))
                        }
                    )
                }
            }
        }
    }
}

const val minPasswordLength = 6

internal val fileJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Composable
fun SpacesExport(spaces: List<EncryptedSpaceInfo>, reportMessage: (String) -> Unit, onDone: () -> Unit) {
    if (spaces.isEmpty()) {
        reportMessage("Nothing to export")
        onDone()
        return
    }
    val saveFilePicker = rememberFileSaverLauncher { file ->
        if (file != null) {
            reportMessage("Exported successfully")
        }
        onDone()
    }
    LaunchedEffect(Unit) {
        saveFilePicker.launch(
            bytes = fileJson.encodeToString(spaces).encodeToByteArray(),
            baseName = if (spaces.size == 1) spaces.single().name else "",
            extension = "json",
        )
    }
}

@Composable
fun SpacesImport(reportMessage: (String) -> Unit, onSpaces: (List<EncryptedSpaceInfo>?) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val filePicker = rememberFilePickerLauncher(
        type = PickerType.File(listOf("json")),
        title = "Export spaces",
    ) { file ->
        if (file == null) {
            onSpaces(null)
            return@rememberFilePickerLauncher
        }
        coroutineScope.launch {
            val spaces = try {
                fileJson.decodeFromString<List<EncryptedSpaceInfo>>(file.readBytes().decodeToString())
            } catch (e: Throwable) {
                reportMessage(e.message ?: "An error occurred")
                onSpaces(null)
                return@launch
            }
            onSpaces(spaces)
            reportMessage("Imported ${spaces.size} space${if (spaces.size == 1) "" else "s"} successfully")
        }
    }

    LaunchedEffect(Unit) {
        filePicker.launch()
    }
}

expect fun Modifier.onExternalSpaces(
    enabled: Boolean = true,
    onDraggingChange: (Boolean) -> Unit = { },
    whenDraging: @Composable Modifier.() -> Modifier = { this },
    onSpace: (List<EncryptedSpaceInfo>) -> Unit,
): Modifier

@Composable
fun NewSpaceDialog(
    cryptoProvider: CryptoProvider,
    onDismissRequest: (Boolean) -> Unit,
    addSpace: (EncryptedSpaceInfo) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordCopy by rememberSaveable { mutableStateOf("") }
    val nameIsCorrect =
        true // name.isNotBlank() // uncomment it to validate it here. Also, implement it for renaming then.
    val passwordIsCorrect = password.length >= minPasswordLength
    val passwordCopyIsCorrect = password == passwordCopy
    val isCorrect = passwordCopyIsCorrect && passwordIsCorrect && nameIsCorrect
    var isLoading by rememberSaveable { mutableStateOf(false) }
    val isSuccess = isCorrect && !isLoading
    fun onSucess() {
        coroutineScope.launch {
            try {
                isLoading = true
                val key = withContext(Dispatchers.Default) {
                    cryptoProvider.generateKey(defaultSCryptConfig, password.toByteArray())
                }
                yield()
                val space = withContext(Dispatchers.Default) {
                    SpaceStructure().toEncryptedBytes(name, key, cryptoProvider)
                }
                yield()
                addSpace(space)
            } finally {
                isLoading = false
            }
            onDismissRequest(true)
        }
    }
    AlertDialog(
        title = { Text("New space") },
        onDismissRequest = { onDismissRequest(false) },
        text = {
            Box {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val nameFocusRequester = remember { FocusRequester() }
                    val passwordFocusRequester = remember { FocusRequester() }
                    val passwordCopyFocusRequester = remember { FocusRequester() }

                    LaunchedEffect(Unit) {
                        nameFocusRequester.requestFocus()
                    }
                    AccountDialogTextField(
                        value = name,
                        onValueChange = { name = it },
                        labelText = "Name",
                        isPassword = false,
                        imeAction = ImeAction.Next,
                        onImeAction = { passwordFocusRequester.requestFocus() },
                        focusRequester = nameFocusRequester,
                        trailingIcon = {
                            TextFieldTrailingErrorIcon(!nameIsCorrect, "Name must not be blank")
                        }
                    )
                    AccountDialogTextField(
                        value = password,
                        onValueChange = { password = it },
                        labelText = "Password",
                        isPassword = true,
                        imeAction = ImeAction.Next,
                        onImeAction = { passwordCopyFocusRequester.requestFocus() },
                        focusRequester = passwordFocusRequester,
                        trailingIcon = {
                            TextFieldTrailingErrorIcon(!passwordIsCorrect, "Too short password")
                        }
                    )
                    AccountDialogTextField(
                        value = passwordCopy,
                        onValueChange = { passwordCopy = it },
                        labelText = "Repeat password",
                        isPassword = true,
                        imeAction = ImeAction.Done,
                        onImeAction = { if (isSuccess) onSucess() },
                        focusRequester = passwordCopyFocusRequester,
                        trailingIcon = {
                            TextFieldTrailingErrorIcon(!passwordCopyIsCorrect, "Password and its copy do not match")
                        }
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        },
        confirmButton = {
            Button(onClick = ::onSucess, enabled = isSuccess) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = { onDismissRequest(false) }) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun EditSpaceDialog(
    cryptoProvider: CryptoProvider,
    oldSpaceInfo: EncryptedSpaceInfo,
    onDismissRequest: () -> Unit,
    replaceSpace: (EncryptedSpaceInfo) -> Unit
) {
    var wrongPasswordTextHeight by remember { mutableStateOf(0.dp) }
    var oldPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var newPasswordCopy by rememberSaveable { mutableStateOf("") }
    val oldPasswordIsCorrect = oldPassword.length >= minPasswordLength
    val newPasswordIsCorrect = newPassword.length >= minPasswordLength
    val newPasswordCopyIsCorrect = newPassword == newPasswordCopy
    val isCorrect = oldPasswordIsCorrect && newPasswordCopyIsCorrect && newPasswordIsCorrect
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var showWrongOldPassword by rememberSaveable(oldPassword) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun onSuccess() {
        coroutineScope.launch {
            try {
                isLoading = true
                val data = withContext(Dispatchers.Default) {
                    oldSpaceInfo.toDecryptedSpaceInfo(cryptoProvider, oldPassword.toByteArray())
                }?.decryptedData
                if (data == null) {
                    showWrongOldPassword = true
                } else {
                    yield()
                    val newKey = withContext(Dispatchers.Default) {
                        cryptoProvider.generateKey(defaultSCryptConfig, newPassword.toByteArray())
                    }
                    yield()
                    val encryptedSpaceInfo = withContext(Dispatchers.Default) {
                        EncryptedSpaceInfo.fromDecryptedData(
                            oldSpaceInfo.name,
                            newKey,
                            data,
                            cryptoProvider
                        )
                    }
                    yield()
                    replaceSpace(encryptedSpaceInfo)
                    onDismissRequest()
                }
            } finally {
                isLoading = false
            }
        }
    }

    val isSuccess = isCorrect && !isLoading
    AlertDialog(
        title = { Text("Edit space \"${oldSpaceInfo.name}\"") },
        onDismissRequest = onDismissRequest,
        text = {
            Box {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val oldPasswordFocusRequester = remember { FocusRequester() }
                    val newPasswordFocusRequester = remember { FocusRequester() }
                    val newPasswordCopyFocusRequester = remember { FocusRequester() }
                    val density = LocalDensity.current

                    LaunchedEffect(Unit) {
                        oldPasswordFocusRequester.requestFocus()
                    }
                    AccountDialogTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        labelText = "Old password",
                        isPassword = true,
                        imeAction = ImeAction.Next,
                        onImeAction = { newPasswordFocusRequester.requestFocus() },
                        focusRequester = oldPasswordFocusRequester,
                        trailingIcon = {
                            TextFieldTrailingErrorIcon(!oldPasswordIsCorrect, "Too short password")
                        }
                    )
                    AccountDialogTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        labelText = "New password",
                        isPassword = true,
                        imeAction = ImeAction.Next,
                        onImeAction = { newPasswordCopyFocusRequester.requestFocus() },
                        focusRequester = newPasswordFocusRequester,
                        trailingIcon = {
                            TextFieldTrailingErrorIcon(!newPasswordIsCorrect, "Too short password")
                        }
                    )
                    AccountDialogTextField(
                        value = newPasswordCopy,
                        onValueChange = { newPasswordCopy = it },
                        labelText = "Repeat new password",
                        isPassword = true,
                        imeAction = ImeAction.Done,
                        onImeAction = { if (isSuccess) onSuccess() },
                        focusRequester = newPasswordCopyFocusRequester,
                        trailingIcon = {
                            TextFieldTrailingErrorIcon(
                                !newPasswordCopyIsCorrect,
                                "Password and its copy do not match"
                            )
                        }
                    )

                    AnimatedVisibility(
                        visible = showWrongOldPassword,
                        modifier = Modifier.onSizeChanged {
                            wrongPasswordTextHeight = density.run { it.height.toDp() }
                        },
                    ) {
                        Text("Incorrect old password", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        },
        confirmButton = {
            Button(onClick = ::onSuccess, enabled = isSuccess) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TextFieldTrailingErrorIcon(visible: Boolean, errorText: String) {
    val animationSpec = tween<Float>(durationMillis = 100, easing = LinearEasing)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = animationSpec),
        exit = fadeOut(animationSpec = animationSpec),
    ) {
        var showPopup by rememberSaveable { mutableStateOf(false) }
        var iconWidth: Int by remember { mutableStateOf(0) }
        Box {
            if (showPopup) {
                Popup(
                    alignment = Alignment.CenterEnd,
                    offset = IntOffset(-iconWidth, 0),
                    onDismissRequest = { showPopup = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    Surface(
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            text = errorText,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
            IconButton(
                onClick = { showPopup = !showPopup },
                modifier = Modifier.onSizeChanged {
                    iconWidth = it.width
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = errorText,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun OpenSpaceDialog(
    cryptoProvider: CryptoProvider,
    spaceInfo: EncryptedSpaceInfo,
    onDismissRequest: () -> Unit,
    onPrivateKeyReceived: (privateKey: PrivateKey) -> Unit,
) {
    var extraHeight by remember { mutableStateOf(0.dp) }
    var password by rememberSaveable { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var showWrongPassword by rememberSaveable(password) { mutableStateOf(false) }
    val passwordIsValid = password.length >= minPasswordLength
    val isSuccess = passwordIsValid && !isLoading

    fun onSuccess() {
        coroutineScope.launch {
            try {
                isLoading = true
                val key = withContext(Dispatchers.Default) {
                    spaceInfo.publicKey.toPrivate(cryptoProvider, password.toByteArray())
                }
                if (key == null) {
                    showWrongPassword = true
                } else {
                    onPrivateKeyReceived(key)
                    onDismissRequest()
                }
            } finally {
                isLoading = false
            }
        }
    }
    AlertDialog(
        title = { Text("Open space \"${spaceInfo.name}\"") },
        onDismissRequest = onDismissRequest,
        text = {
            Box {
                val passwordFocusRequester = remember { FocusRequester() }
                val density = LocalDensity.current
                LaunchedEffect(Unit) {
                    passwordFocusRequester.requestFocus()
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AccountDialogTextField(
                        value = password,
                        onValueChange = { password = it },
                        labelText = "Password",
                        isPassword = true,
                        imeAction = ImeAction.Done,
                        onImeAction = { if (isSuccess) onSuccess() },
                        focusRequester = passwordFocusRequester,
                        trailingIcon = {
                            TextFieldTrailingErrorIcon(!passwordIsValid, "Too short password")
                        }
                    )

                    AnimatedVisibility(
                        showWrongPassword,
                        modifier = Modifier.onSizeChanged { extraHeight = density.run { it.height.toDp() } }) {
                        Text("Incorrect password", color = MaterialTheme.colorScheme.error)
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        },
        confirmButton = {
            Button(enabled = isSuccess, onClick = ::onSuccess) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun AccountDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelText: String,
    isPassword: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    focusRequester: FocusRequester,
    trailingIcon: @Composable (() -> Unit)? = null,
) = TextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(labelText) },
    singleLine = true,
    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
    keyboardOptions = KeyboardOptions(
        imeAction = imeAction,
        capitalization = if (isPassword) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
        autoCorrect = !isPassword,
        keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
    ),
    keyboardActions = KeyboardActions(
        onAny = { onImeAction() },
    ),
    modifier = Modifier.focusRequester(focusRequester).clip(RoundedCornerShape(12.dp)),
    trailingIcon = trailingIcon,
    colors = TextFieldDefaults.colors(
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    ),
)

@Composable
expect fun DialogSurface(content: @Composable () -> Unit)
