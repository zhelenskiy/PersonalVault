package startScreen

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.DpSize
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
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import spaceScreen.SpaceScreen

class SpaceListScreen : Screen {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel<SpaceListScreenModel>()
        val spaces by screenModel.spaces.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        SpaceListScreenContent(
            cryptoProvider = screenModel.cryptoProvider,
            spaces = spaces,
            onSpacesChange = screenModel::setSpaces,
            onSpaceOpen = { navigator += SpaceScreen(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceListScreenContent(
    cryptoProvider: CryptoProvider,
    spaces: PersistentList<EncryptedSpaceInfo>,
    onSpacesChange: (list: PersistentList<EncryptedSpaceInfo>) -> Unit,
    onSpaceOpen: (DecryptedSpaceInfo) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Spaces",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
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
                        text = "Are you sure you want to delete space \"${space.name}\"?",
                        deleteSpace = { onSpacesChange(spaces.removeAt(index)) },
                        closeDialog = onClose,
                    )
                },
                onItemClick = { _, space, onClose ->
                    OpenSpaceDialog(
                        cryptoProvider = cryptoProvider,
                        spaceInfo = space,
                        onDismissRequest = onClose,
                        openSpace = onSpaceOpen,
                    )
                },
            ) { index, space ->
                CardTextField(
                    space.name,
                    onValueChange = {
                        onSpacesChange(
                            spaces.set(
                                index = index,
                                element = EncryptedSpaceInfo(it, space.publicKey, space.encryptedData)
                            )
                        )
                    })
            }
        }
    }
}

const val minPasswordLength = 6

@Composable
fun NewSpaceDialog(
    cryptoProvider: CryptoProvider,
    onDismissRequest: () -> Unit,
    addSpace: (EncryptedSpaceInfo) -> Unit
) {
    NativeDialog(
        title = "New space",
        size = DpSize(400.dp, 250.dp),
        onDismissRequest = onDismissRequest,
    ) {
        var name by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var passwordCopy by rememberSaveable { mutableStateOf("") }
        val nameIsCorrect = name.isNotBlank()
        val passwordIsCorrect = password.length >= minPasswordLength
        val passwordCopyIsCorrect = password == passwordCopy
        val isCorrect = passwordCopyIsCorrect && passwordIsCorrect && nameIsCorrect
        var isLoading by rememberSaveable { mutableStateOf(false) }
        Box {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val nameFocusRequester = remember { FocusRequester() }
                val passwordFocusRequester = remember { FocusRequester() }
                val passwordCopyFocusRequester = remember { FocusRequester() }
                val okFocusRequester = remember { FocusRequester() }

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
                    onImeAction = { okFocusRequester.requestFocus() },
                    focusRequester = passwordCopyFocusRequester,
                    trailingIcon = {
                        TextFieldTrailingErrorIcon(!passwordCopyIsCorrect, "Password and its copy do not match")
                    }
                )

                val acceptButtonColor by animateColorAsState(
                    targetValue = when {
                        isLoading -> Color.Transparent
                        isCorrect -> LocalContentColor.current
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                val coroutineScope = rememberCoroutineScope()
                DialogButtons(
                    enableClickingSuccessButton = isCorrect && !isLoading,
                    onDismissRequest = onDismissRequest,
                    onSuccess = {
                        coroutineScope.launch {
                            try {
                                isLoading = true
                                val key = withContext(Dispatchers.Default) {
                                    cryptoProvider.generateKey(defaultSCryptConfig, password.toByteArray())
                                }
                                yield()
                                val data = "cat".toByteArray()
                                val space = withContext(Dispatchers.Default) {
                                    EncryptedSpaceInfo.fromDecryptedData(name, key, data, cryptoProvider)
                                }
                                yield()
                                addSpace(space)
                            } finally {
                                isLoading = false
                            }
                            onDismissRequest()
                        }
                    },
                    focusRequester = okFocusRequester,
                    acceptButtonColor = acceptButtonColor,
                )
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun EditSpaceDialog(
    cryptoProvider: CryptoProvider,
    oldSpaceInfo: EncryptedSpaceInfo,
    onDismissRequest: () -> Unit,
    replaceSpace: (EncryptedSpaceInfo) -> Unit
) {
    var wrongPasswordTextHeight by remember { mutableStateOf(0.dp) }
    NativeDialog(
        title = "Edit space \"${oldSpaceInfo.name}\"",
        size = DpSize(400.dp, 250.dp + wrongPasswordTextHeight),
        onDismissRequest = onDismissRequest,
    ) {
        var oldPassword by rememberSaveable { mutableStateOf("") }
        var newPassword by rememberSaveable { mutableStateOf("") }
        var newPasswordCopy by rememberSaveable { mutableStateOf("") }
        val oldPasswordIsCorrect = oldPassword.length >= minPasswordLength
        val newPasswordIsCorrect = newPassword.length >= minPasswordLength
        val newPasswordCopyIsCorrect = newPassword == newPasswordCopy
        val isCorrect = oldPasswordIsCorrect && newPasswordCopyIsCorrect && newPasswordIsCorrect
        var isLoading by rememberSaveable { mutableStateOf(false) }
        var showWrongOldPassword by rememberSaveable(oldPassword) { mutableStateOf(false) }
        Box {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val oldPasswordFocusRequester = remember { FocusRequester() }
                val newPasswordFocusRequester = remember { FocusRequester() }
                val newPasswordCopyFocusRequester = remember { FocusRequester() }
                val okFocusRequester = remember { FocusRequester() }
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
                    onImeAction = { okFocusRequester.requestFocus() },
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
                    modifier = Modifier.onSizeChanged { wrongPasswordTextHeight = density.run { it.height.toDp() } },
                ) {
                    Text("Incorrect old password", color = MaterialTheme.colorScheme.error)
                }

                val acceptButtonColor by animateColorAsState(
                    targetValue = when {
                        isLoading -> Color.Transparent
                        isCorrect -> LocalContentColor.current
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                val coroutineScope = rememberCoroutineScope()
                DialogButtons(
                    enableClickingSuccessButton = isCorrect && !isLoading,
                    onDismissRequest = onDismissRequest,
                    onSuccess = {
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
                                        EncryptedSpaceInfo.fromDecryptedData(oldSpaceInfo.name, newKey, data, cryptoProvider)
                                    }
                                    yield()
                                    replaceSpace(encryptedSpaceInfo)
                                    onDismissRequest()
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    focusRequester = okFocusRequester,
                    acceptButtonColor = acceptButtonColor,
                )
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
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
    openSpace: (privateKey: DecryptedSpaceInfo) -> Unit,
) {
    var extraHeight by remember { mutableStateOf(0.dp) }
    NativeDialog(
        title = "Open space \"${spaceInfo.name}\"",
        size = DpSize(400.dp, 135.dp + extraHeight),
        onDismissRequest = onDismissRequest,
    ) {
        Box {
            var password by rememberSaveable { mutableStateOf("") }
            val passwordFocusRequester = remember { FocusRequester() }
            val passwordIsValid = password.length >= minPasswordLength
            val okFocusRequester = remember { FocusRequester() }
            var isLoading by rememberSaveable { mutableStateOf(false) }
            var showWrongPassword by rememberSaveable(password) { mutableStateOf(false) }
            val density = LocalDensity.current
            LaunchedEffect(Unit) {
                passwordFocusRequester.requestFocus()
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AccountDialogTextField(
                    value = password,
                    onValueChange = { password = it },
                    labelText = "Password",
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onImeAction = { okFocusRequester.requestFocus() },
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

                val coroutineScope = rememberCoroutineScope()

                val acceptButtonColor by animateColorAsState(
                    targetValue = when {
                        isLoading -> Color.Transparent
                        passwordIsValid -> LocalContentColor.current
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                DialogButtons(
                    enableClickingSuccessButton = passwordIsValid && !isLoading,
                    onDismissRequest = onDismissRequest,
                    onSuccess = {
                        coroutineScope.launch {
                            try {
                                isLoading = true
                                val space = withContext(Dispatchers.Default) {
                                    spaceInfo.toDecryptedSpaceInfo(cryptoProvider, password.toByteArray())
                                }
                                if (space == null) {
                                    showWrongPassword = true
                                } else {
                                    openSpace(space)
                                    onDismissRequest()
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    focusRequester = okFocusRequester,
                    acceptButtonColor = acceptButtonColor,
                )
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
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
    modifier = Modifier
        .focusRequester(focusRequester)
        .fillMaxWidth(),
    trailingIcon = trailingIcon,
)

@Composable
expect fun DialogSurface(content: @Composable () -> Unit)
