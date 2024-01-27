package startScreen

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import common.NativeDialog
import common.VerticalScrollbar
import crypto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesScreen(cryptoProvider: CryptoProvider, spaces: List<EncryptedSpaceInfo>, onSpacesChange: (list: List<EncryptedSpaceInfo>) -> Unit, onSpaceOpen: (DecryptedSpaceInfo) -> Unit) {
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
        val scrollState = rememberScrollState()
        Box(Modifier.padding(paddingValues)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(10.dp)
                    .verticalScroll(scrollState)
                    .fillMaxWidth(),
            ) {
                if (spaces.isEmpty()) {
                    Text("No spaces yet")
                }

                for (space in spaces) {
                    var showOpenSpaceDialog by remember { mutableStateOf(false) }
                    val shape = MaterialTheme.shapes.large
                    Card(
                        shape = shape,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(shape)
                            .clickable { showOpenSpaceDialog = true },
                    ) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = space.name,
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .weight(1f),
                            )

                            var showChangeDialog by rememberSaveable { mutableStateOf(false) }
                            IconButton(
                                onClick = { showChangeDialog = true },
                                modifier = Modifier
                            ) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                            if (showChangeDialog) {
                                EditSpaceDialog(
                                    cryptoProvider = cryptoProvider,
                                    oldSpaceInfo = space,
                                    onDismissRequest = { showChangeDialog = false },
                                    replaceSpace = { newSpace ->
                                        onSpacesChange(spaces.map { if (it == space) newSpace else it })
                                    }
                                )
                            }

                            var showDeletionConfirmation by rememberSaveable { mutableStateOf(false) }
                            IconButton(
                                onClick = { showDeletionConfirmation = true },
                                modifier = Modifier
                            ) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                            if (showDeletionConfirmation) {
                                DeletionConfirmation(
                                    space = space,
                                    deleteSpace = { onSpacesChange(spaces - space) },
                                    closeDialog = { showDeletionConfirmation = false }
                                )
                            }
                            if (showOpenSpaceDialog) {
                                OpenSpaceDialog(
                                    cryptoProvider = cryptoProvider,
                                    spaceInfo = space,
                                    onDismissRequest = { showOpenSpaceDialog = false },
                                    openSpace = onSpaceOpen,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                var showNewPopup by rememberSaveable { mutableStateOf(false) }
                IconButton(onClick = { showNewPopup = true }) {
                    Icon(Icons.Default.Add, "New space")
                }
                if (showNewPopup) {
                    NewSpaceDialog(
                        cryptoProvider = cryptoProvider,
                        onDismissRequest = { showNewPopup = false },
                        addSpace = { onSpacesChange(spaces + it) }
                    )
                }
            }
            VerticalScrollbar(scrollState)
        }
    }
}

@Composable
private fun DeletionConfirmation(
    space: EncryptedSpaceInfo,
    deleteSpace: () -> Unit,
    closeDialog: () -> Unit,
) {
    NativeDialog("Deleting space", size = DpSize(400.dp, 120.dp), onDismissRequest = closeDialog) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Are you sure you want to delete space \"${space.name}\"?",
                modifier = Modifier.padding(8.dp),
                textAlign = TextAlign.Center,
            )
            DialogButtons(
                enableClickingSuccessButton = true,
                onDismissRequest = closeDialog,
                onSuccess = { deleteSpace(); closeDialog() },
            )
        }
    }
}

const val minPasswordLength = 6

@Composable
fun NewSpaceDialog(cryptoProvider: CryptoProvider, onDismissRequest: () -> Unit, addSpace: (EncryptedSpaceInfo) -> Unit) {
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
    var changePassword by rememberSaveable { mutableStateOf(false) }
    fun <T> animationSpec() = tween<T>(durationMillis = 600)

    val dialogHeight by animateDpAsState(if (changePassword) 350.dp else 190.dp, animationSpec = animationSpec())
    NativeDialog(
        title = "Edit space \"${oldSpaceInfo.name}\"",
        size = DpSize(400.dp, dialogHeight),
        onDismissRequest = onDismissRequest,
    ) {
        var newName by rememberSaveable { mutableStateOf(oldSpaceInfo.name) }

        var oldPassword by rememberSaveable { mutableStateOf("") }
        var newPassword by rememberSaveable { mutableStateOf("") }
        var newPasswordCopy by rememberSaveable { mutableStateOf("") }
        val newNameIsCorrect = newName.isNotBlank()
        val oldPasswordIsCorrect = oldPassword.length >= minPasswordLength
        val newPasswordIsCorrect = newPassword.length >= minPasswordLength
        val newPasswordCopyIsCorrect = newPassword == newPasswordCopy
        val isCorrect =
            (!changePassword || oldPasswordIsCorrect && newPasswordCopyIsCorrect && newPasswordIsCorrect) && newNameIsCorrect
        var isLoading by rememberSaveable { mutableStateOf(false) }
        var showWrongOldPassword by rememberSaveable(oldPassword) { mutableStateOf(false) }
        Box {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val newNameFocusRequester = remember { FocusRequester() }
                val changePasswordFocusRequester = remember { FocusRequester() }
                val oldPasswordFocusRequester = remember { FocusRequester() }
                val newPasswordFocusRequester = remember { FocusRequester() }
                val newPasswordCopyFocusRequester = remember { FocusRequester() }
                val okFocusRequester = remember { FocusRequester() }

                LaunchedEffect(Unit) {
                    newNameFocusRequester.requestFocus()
                }
                AccountDialogTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    labelText = "Name",
                    isPassword = false,
                    imeAction = ImeAction.Next,
                    onImeAction = { changePasswordFocusRequester.requestFocus() },
                    focusRequester = newNameFocusRequester,
                    trailingIcon = {
                        TextFieldTrailingErrorIcon(!newNameIsCorrect, "Name must not be blank")
                    }
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text("Change password")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = changePassword,
                        onCheckedChange = { changePassword = it },
                        modifier = Modifier.focusRequester(changePasswordFocusRequester),
                    )
                }

                AnimatedVisibility(changePassword, enter = expandVertically(animationSpec()), exit = shrinkVertically(animationSpec())) {
                    Column {
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
                    }
                }

                AnimatedVisibility(showWrongOldPassword) {
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
                        if (changePassword) {
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
                                            EncryptedSpaceInfo.fromDecryptedData(newName, newKey, data, cryptoProvider)
                                        }
                                        yield()
                                        replaceSpace(encryptedSpaceInfo)
                                        onDismissRequest()
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            replaceSpace(EncryptedSpaceInfo(newName, oldSpaceInfo.publicKey, oldSpaceInfo.encryptedData))
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
private fun TextFieldTrailingErrorIcon(visible: Boolean, errorText: String) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
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
    NativeDialog(
        title = "Open space \"${spaceInfo.name}\"",
        size = DpSize(400.dp, 200.dp),
        onDismissRequest = onDismissRequest,
    ) {
        Box {
            var password by rememberSaveable { mutableStateOf("") }
            val passwordFocusRequester = remember { FocusRequester() }
            val passwordIsValid = password.length >= minPasswordLength
            val okFocusRequester = remember { FocusRequester() }
            var isLoading by rememberSaveable { mutableStateOf(false) }
            var showWrongPassword by rememberSaveable(password) { mutableStateOf(false) }
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
                
                AnimatedVisibility(showWrongPassword) {
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
private fun DialogButtons(
    enableClickingSuccessButton: Boolean,
    onDismissRequest: () -> Unit,
    onSuccess: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    acceptButtonColor: Color = LocalContentColor.current,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        IconButton(
            onClick = {
                if (enableClickingSuccessButton) {
                    onSuccess()
                }
            },
            enabled = enableClickingSuccessButton,
            modifier = Modifier.focusRequester(focusRequester),
        ) {
            Icon(Icons.Default.Done, tint = acceptButtonColor, contentDescription = "Finish creating")
        }

        IconButton(onClick = onDismissRequest) {
            Icon(Icons.Default.Close, "Cancel")
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
