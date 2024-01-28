package common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import common.NativeDialog


@Composable
fun <T> ModifiableList(
    items: List<T>,
    onEmptyContent: @Composable () -> Unit,
    onCreateItemRequest: (@Composable (onEnd: () -> Unit) -> Unit)? = null,
    onChangeItemRequest: (@Composable (index: Int, item: T, onEnd: () -> Unit) -> Unit)? = null,
    onDeleteItemRequest: (@Composable (index: Int, item: T, onEnd: () -> Unit) -> Unit)? = null,
    onItemClick: @Composable ((index: Int, item: T, onEnd: () -> Unit) -> Unit)? = null,
    content: @Composable RowScope.(index: Int, item: T) -> Unit
) {
    val lazyListState = rememberLazyListState()
    Box {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            state = lazyListState,
        ) {
            item {
                if (items.isEmpty()) {
                    onEmptyContent()
                }
            }

            itemsIndexed(items) { index, item ->
                val shape = MaterialTheme.shapes.large
                var changeIsActive by rememberSaveable { mutableStateOf(false) }
                var deleteIsActive by rememberSaveable { mutableStateOf(false) }
                var clickIsActive by rememberSaveable { mutableStateOf(false) }
                Card(
                    shape = shape,
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(shape)
                        .run { if (onItemClick != null) clickable { clickIsActive = true } else this },
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {

                        content(index, item)

                        if (onChangeItemRequest != null) {
                            IconButton(
                                onClick = { changeIsActive = true },
                                modifier = Modifier
                            ) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                            if (changeIsActive) {
                                onChangeItemRequest(index, item, onEnd = { changeIsActive = false })
                            }
                        }

                        if (onDeleteItemRequest != null) {
                            IconButton(
                                onClick = { deleteIsActive = true },
                                modifier = Modifier
                            ) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                            if (deleteIsActive) {
                                onDeleteItemRequest(index, item, onEnd = { deleteIsActive = false })
                            }
                        }

                        if (onItemClick != null && clickIsActive) {
                            onItemClick(index, item, onEnd = { clickIsActive = false })
                        }
                    }
                }
            }

            if (onCreateItemRequest != null) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    var creatingIsActive by rememberSaveable { mutableStateOf(false) }
                    IconButton(onClick = { creatingIsActive = true }) {
                        Icon(Icons.Default.Add, "Create")
                    }
                    if (creatingIsActive) {
                        onCreateItemRequest(onEnd = { creatingIsActive = false })
                    }
                }
            }
        }
        VerticalScrollbar(lazyListState)
    }
}

@Composable
fun DeletionConfirmation(
    windowTitle: String,
    text: String,
    deleteSpace: () -> Unit,
    closeDialog: () -> Unit,
) {
    NativeDialog(windowTitle, size = DpSize(400.dp, 120.dp), onDismissRequest = closeDialog) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
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


@Composable
fun DialogButtons(
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RowScope.CardTextField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val enabled = true
    val singleLine = true
    Box(Modifier.weight(1f)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .width(IntrinsicSize.Min)
                .onKeyEvent {
                    if (!it.isAltPressed && !it.isCtrlPressed && !it.isShiftPressed && !it.isMetaPressed && it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                        keyboardController?.hide()
                        focusManager.clearFocus(true)
                    }
                    false
                },
            textStyle = MaterialTheme.typography.titleMedium,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrect = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onAny = {
                    keyboardController?.hide()
                    focusManager.clearFocus(true)
                }
            ),
            interactionSource = interactionSource,
            enabled = enabled,
            singleLine = singleLine,
            decorationBox = { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    placeholder = { Text("Untitled") },
                    interactionSource = interactionSource,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = VisualTransformation.None,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                        start = 8.dp,
                        end = 8.dp,
                    ),
                )
            }
        )
    }
}