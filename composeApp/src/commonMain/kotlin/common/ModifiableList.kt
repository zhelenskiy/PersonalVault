package common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ModifiableListItemDecoration(
    onChangeItemRequest: @Composable ((onEnd: () -> Unit) -> Unit)? = null,
    onDeleteItemRequest: @Composable ((onEnd: () -> Unit) -> Unit)? = null,
    onChangeItemEnabled: Boolean = true,
    onDeleteItemEnabled: Boolean = true,
    deleteIcon: ImageVector = Icons.Default.Delete,
) {
    var changeIsActive by rememberSaveable { mutableStateOf(false) }
    var deleteIsActive by rememberSaveable { mutableStateOf(false) }

    AnimatedVisibility(onChangeItemRequest != null) {
        IconButton(
            onClick = { changeIsActive = true },
            modifier = Modifier,
            enabled = onChangeItemEnabled,
        ) {
            Icon(Icons.Default.Edit, "Edit")
        }
        if (changeIsActive && onChangeItemRequest != null) {
            onChangeItemRequest { changeIsActive = false }
        }
    }

    AnimatedVisibility(onDeleteItemRequest != null) {
        IconButton(
            onClick = { deleteIsActive = true },
            modifier = Modifier,
            enabled = onDeleteItemEnabled,
        ) {
            Icon(deleteIcon, "Delete")
        }
        if (deleteIsActive && onDeleteItemRequest != null) {
            onDeleteItemRequest { deleteIsActive = false }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
inline fun <T, reified K : Any> ModifiableList(
    items: List<T>,
    noinline key: (T) -> K,
    crossinline indexByKey: (K) -> Int,
    crossinline changeOrder: (from: Int, to: Int) -> Unit,
    crossinline onEmptyContent: @Composable () -> Unit,
    noinline onCreateItemRequest: (@Composable (onEnd: (Boolean) -> Unit) -> Unit)? = null,
    noinline onChangeItemRequest: (@Composable (index: Int, item: T, onEnd: () -> Unit) -> Unit)? = null,
    noinline onDeleteItemRequest: (@Composable (index: Int, item: T, onEnd: () -> Unit) -> Unit)? = null,
    noinline onItemClick: @Composable ((index: Int, item: T, onEnd: () -> Unit) -> Unit)? = null,
    noinline onItemExport: (@Composable (index: Int, item: T, onEnd: () -> Unit) -> Unit)? = null,
    noinline onItemsUpload: (@Composable ((Boolean) -> Unit) -> Unit)? = null,
    lazyListState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    crossinline content: @Composable RowScope.(index: Int, item: T) -> Unit,
) {
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = indexByKey(from.key as? K ?: return@rememberReorderableLazyListState)
        val toIndex = indexByKey(to.key as? K ?: return@rememberReorderableLazyListState)
        changeOrder(fromIndex, toIndex)
    }
    val coroutineScope = rememberCoroutineScope()
    Box(modifier) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
        ) {
            item {
                if (items.isEmpty()) {
                    onEmptyContent()
                }
            }

            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                ReorderableItem(reorderableLazyListState, key = key(item)) { isDragging ->
                    val shape = MaterialTheme.shapes.large
                    var clickIsActive by rememberSaveable { mutableStateOf(false) }
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)

                    Card(
                        shape = shape,
                        modifier = Modifier
                            .draggableHandle()
                            .padding(8.dp)
                            .clip(shape)
                            .run { if (onItemClick != null) clickable { clickIsActive = true } else this },
                        elevation = CardDefaults.cardElevation(
                            elevation, elevation, elevation, elevation, elevation, elevation
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            content(index, item)

                            if (onItemExport != null) {
                                var exportIsActive by rememberSaveable { mutableStateOf(false) }
                                IconButton(onClick = { exportIsActive = !exportIsActive }) {
                                    Icon(Icons.Default.Download, "Export")
                                }
                                if (exportIsActive) {
                                    onItemExport(index, item) { exportIsActive = false }
                                }
                            }

                            ModifiableListItemDecoration(
                                onChangeItemRequest = onChangeItemRequest?.let {
                                    { onEnd -> onChangeItemRequest(index, item, onEnd) }
                                },
                                onDeleteItemRequest = onDeleteItemRequest?.let {
                                    { onEnd -> onDeleteItemRequest(index, item, onEnd) }
                                },
                            )

                            if (onItemClick != null && clickIsActive) {
                                onItemClick(index, item) { clickIsActive = false }
                            }
                        }
                    }
                }
            }

            if (onCreateItemRequest != null || onItemsUpload != null) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    ) {
                        if (onCreateItemRequest != null) {
                            var creatingIsActive by rememberSaveable { mutableStateOf(false) }
                            IconButton(onClick = { creatingIsActive = !creatingIsActive }) {
                                Icon(Icons.Default.Add, "Create")
                            }
                            if (creatingIsActive) {
                                onCreateItemRequest { success ->
                                    creatingIsActive = false
                                    if (success) {
                                        coroutineScope.launch {
                                            lazyListState.animateScrollToItem(Int.MAX_VALUE)
                                        }
                                    }
                                }
                            }
                        }
                        if (onItemsUpload != null) {
                            var uploadingIsActive by rememberSaveable { mutableStateOf(false) }
                            IconButton(onClick = { uploadingIsActive = !uploadingIsActive }) {
                                Icon(Icons.Default.Upload, "Import")
                            }
                            if (uploadingIsActive) {
                                onItemsUpload { success ->
                                    uploadingIsActive = false
                                    if (success) {
                                        coroutineScope.launch {
                                            lazyListState.animateScrollToItem(Int.MAX_VALUE)
                                        }
                                    }
                                }
                            }
                        }
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
    delete: () -> Unit,
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
                onSuccess = { delete(); closeDialog() },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.CardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val enabled = true
    val singleLine = true
    Box(modifier.weight(1f).padding(start = 8.dp)) {
        val textMeasurer = rememberTextMeasurer()
        var width by remember { mutableStateOf<Int?>(null) }
        val textStyle = MaterialTheme.typography.titleMedium
        val density = LocalDensity.current
        val emptyText = "Untitled"
        LaunchedEffect(value) {
            width = textMeasurer.measure(value.ifEmpty { emptyText }, textStyle).size.width
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .width(density.run { (width ?: return).toDp() + 4.dp })
                .onKeyEvent {
                    if (!it.isAltPressed && !it.isCtrlPressed && !it.isShiftPressed && !it.isMetaPressed && it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                        keyboardController?.hide()
                        focusManager.clearFocus(true)
                    }
                    false
                },
            textStyle = textStyle,
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
            readOnly = readOnly,
            interactionSource = interactionSource,
            enabled = enabled,
            singleLine = singleLine,
            decorationBox = { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    placeholder = { Text(emptyText) },
                    interactionSource = interactionSource,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = VisualTransformation.None,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                    contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                        start = 0.dp, end = 0.dp, top = 0.dp, bottom = 0.dp,
                    ),
                )
            }
        )
    }
}
