package editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.kodein.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.godaddy.android.colorpicker.ClassicColorPicker
import com.godaddy.android.colorpicker.HsvColor
import common.*
import common.FileSystemItem.FileId
import crypto.PrivateKey
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

class FileEditorScreen(private val index: Int, private val cryptoKey: PrivateKey, private val fileId: FileId) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel =
            rememberScreenModel<_, FileEditorScreenModel>(arg = FileEditorScreenInfo(index, cryptoKey, fileId))
        val file by screenModel.fileSystemFlow.collectAsState()
        val isSaving by screenModel.isSaving.collectAsState()
        FileEditorScreenContent(
            file = file,
            backPress = { navigator.pop() },
            onHomePress = { navigator.popUntilRoot() },
            onFileChanged = { screenModel.setFile(screenModel.getNewVersionNumber(), it) },
            isSaving = isSaving,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreenContent(
    file: File?,
    backPress: () -> Unit,
    onHomePress: () -> Unit,
    onFileChanged: (file: File) -> Unit,
    isSaving: Boolean,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = backPress) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (file == null) return@CenterAlignedTopAppBar
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(IntrinsicSize.Min)) {
                        file.type.icon()
                        Spacer(Modifier.width(8.dp))
                        CardTextField(file.name) { onFileChanged(File(it, file.type, file.content)) }
                    }
                },
                actions = {
                    if (isSaving) {
                        SyncIndicator()
                    }

                    IconButton(onClick = onHomePress) {
                        Icon(Icons.Default.Home, contentDescription = "To start page")
                    }
                },
            )
        }
    ) {
        Box(Modifier.windowInsetsPadding(WindowInsets.ime).padding(it)) {
            when (file) {
                null -> {}
                is TextFile -> TextFileEditor(file, onFileChanged)
            }
        }
    }
}

@Composable
fun TextFileEditor(file: TextFile, onFileChanged: (TextFile) -> Unit) {
    val (textFieldValue, onTextFieldValueChange) = remember { mutableStateOf(TextFieldValue(file.text)) }
    LaunchedEffect(textFieldValue.text) {
        if (file.text != textFieldValue.text) {
            when (file) {
                is MarkupTextFile -> onFileChanged(MarkupTextFile(name = file.name, type = file.type, text = textFieldValue.text, editorMode = file.editorMode))
                is PlainTextFile -> onFileChanged(PlainTextFile(name = file.name, text = textFieldValue.text))
            }
        }
    }
    when (file) {
        is MarkupTextFile -> MarkedUpTextFileEditor(
            textFieldValue = textFieldValue,
            fileType = file.type,
            editorMode = file.editorMode,
            onEditorModeChange = {
                onFileChanged(
                    MarkupTextFile(
                        name = file.name,
                        type = file.type,
                        text = textFieldValue.text,
                        editorMode = it
                    )
                )
            },
            onTextFieldValueChange = onTextFieldValueChange
        )

        is PlainTextFile -> PlainTextFileEditor(
            textFieldValue, FontFamily.Default, onTextFieldValueChange = onTextFieldValueChange
        )
    }
}

@Composable
private fun PlainTextFileEditor(
    textFieldValue: TextFieldValue,
    fontFamily: FontFamily,
    onTextFieldValueChange: (TextFieldValue) -> Unit
) {
    BasicTextField(
        value = textFieldValue,
        onValueChange = onTextFieldValueChange,
        textStyle = TextStyle.Default.copy(fontFamily = fontFamily),
        modifier = Modifier
            .padding(8.dp)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
    )
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun MarkedUpTextFileEditor(
    textFieldValue: TextFieldValue,
    fileType: MarkupTextFileType,
    editorMode: MarkedUpTextFileEditorMode,
    onEditorModeChange: (editorMode: MarkedUpTextFileEditorMode) -> Unit,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
) {
    val actions = when (fileType) {
        MarkupTextFileType.Html -> htmlActions
        MarkupTextFileType.Markdown -> markdownActions
    }
    val mode = editorMode
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.height(IntrinsicSize.Min).animateContentSize(tween(300, easing = FastOutLinearInEasing))
                .width(IntrinsicSize.Min)
        ) {

            @Composable
            fun Divider() = Spacer(
                modifier = Modifier
                    .width(0.5.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onPrimaryContainer)
                    .padding(vertical = 8.dp),
            )

            Row {
                val chosenColor = MaterialTheme.colorScheme.primary
                val unchosenColor = Color.Transparent
                IconButton(
                    onClick = { onEditorModeChange(MarkedUpTextFileEditorMode.Source) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (mode == MarkedUpTextFileEditorMode.Source) chosenColor else unchosenColor,
                        contentColor = contentColorFor(if (mode == MarkedUpTextFileEditorMode.Source) chosenColor else unchosenColor),
                    ),
                ) {
                    Icon(Icons.Default.ViewHeadline, contentDescription = "Source")
                }

                IconButton(
                    onClick = { onEditorModeChange(MarkedUpTextFileEditorMode.Both) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (mode == MarkedUpTextFileEditorMode.Both) chosenColor else unchosenColor,
                        contentColor = contentColorFor(if (mode == MarkedUpTextFileEditorMode.Both) chosenColor else unchosenColor),
                    ),
                ) {
                    Icon(Icons.Default.VerticalSplit, contentDescription = "Both")
                }

                IconButton(
                    onClick = { onEditorModeChange(MarkedUpTextFileEditorMode.Preview) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (mode == MarkedUpTextFileEditorMode.Preview) chosenColor else unchosenColor,
                        contentColor = contentColorFor(if (mode == MarkedUpTextFileEditorMode.Preview) chosenColor else unchosenColor),
                    ),
                ) {
                    Icon(Icons.Default.Image, "Preview")
                }
            }
            AnimatedVisibility(mode != MarkedUpTextFileEditorMode.Preview) {
                Divider()
            }
            AnimatedVisibility(mode != MarkedUpTextFileEditorMode.Preview, Modifier) {
                Row(Modifier.horizontalScroll(rememberScrollState()).width(IntrinsicSize.Min)) {
                    IconButton(
                        modifier = Modifier.focusProperties { canFocus = false },
                        onClick = { onTextFieldValueChange(actions.bold.applyAction(textFieldValue)) },
                    ) {
                        Icon(Icons.Default.FormatBold, contentDescription = "Bold")
                    }
                    IconButton(
                        modifier = Modifier.focusProperties { canFocus = false },
                        onClick = { onTextFieldValueChange(actions.italic.applyAction(textFieldValue)) },
                    ) {
                        Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
                    }
                    IconButton(
                        modifier = Modifier.focusProperties { canFocus = false },
                        onClick = { onTextFieldValueChange(actions.underline.applyAction(textFieldValue)) },
                    ) {
                        Icon(Icons.Default.FormatUnderlined, contentDescription = "Underlined")
                    }
                    IconButton(
                        modifier = Modifier.focusProperties { canFocus = false },
                        onClick = { onTextFieldValueChange(actions.strikeThrough.applyAction(textFieldValue)) },
                    ) {
                        Icon(Icons.Default.FormatStrikethrough, contentDescription = "Strikethrough")
                    }
                    IconButton(
                        modifier = Modifier.focusProperties { canFocus = false },
                        onClick = { onTextFieldValueChange(actions.overline.applyAction(textFieldValue)) },
                    ) {
                        Icon(Icons.Default.FormatOverline, contentDescription = "Overline")
                    }
                    var showForegroundColorPicker by rememberSaveable { mutableStateOf(false) }
                    var showBackgroundColorPicker by rememberSaveable { mutableStateOf(false) }
                    val density = LocalDensity.current
                    fun BoxWithConstraintsScope.colorPickerOffset() = density.run {
                        IntOffset(
                            x = -128.dp.roundToPx(), // because of https://github.com/JetBrains/compose-multiplatform/issues/1062
                            y = (maxHeight + 8.dp).roundToPx()
                        )
                    }
                    IconButton(
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .background(
                                if (showForegroundColorPicker) MaterialTheme.colorScheme.primary else Color.Transparent,
                                CircleShape
                            ),
                        onClick = {
                            showForegroundColorPicker = !showForegroundColorPicker
                            showBackgroundColorPicker = false
                        },
                    ) {
                        BoxWithConstraints {
                            Icon(Icons.Default.FormatColorText, contentDescription = "Foreground color")
                            if (showForegroundColorPicker) {
                                ColorPickerPopup(offset = colorPickerOffset()) { color ->
                                    if (color != null) {
                                        onTextFieldValueChange(
                                            actions.foregroundColor.applyAction(textFieldValue, color)
                                        )
                                    }
                                    showForegroundColorPicker = false
                                }
                            }
                        }
                    }
                    IconButton(
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .background(
                                if (showBackgroundColorPicker) MaterialTheme.colorScheme.primary else Color.Transparent,
                                CircleShape
                            ),
                        onClick = {
                            showBackgroundColorPicker = !showBackgroundColorPicker
                            showForegroundColorPicker = false
                        },
                    ) {
                        BoxWithConstraints {
                            Icon(Icons.Default.FormatColorFill, contentDescription = "Background color")
                            if (showBackgroundColorPicker) {
                                ColorPickerPopup(offset = colorPickerOffset()) { color ->
                                    if (color != null) {
                                        onTextFieldValueChange(
                                            actions.backgroundColor.applyAction(textFieldValue, color)
                                        )
                                    }
                                    showBackgroundColorPicker = false
                                }
                            }
                        }
                    }
                    IconButton(
                        modifier = Modifier.focusProperties { canFocus = false },
                        onClick = { onTextFieldValueChange(actions.listBulleted.applyAction(textFieldValue)) },
                    ) {
                        Icon(Icons.Default.FormatListBulleted, contentDescription = "List bulleted")
                    }
                    IconButton(
                        modifier = Modifier.focusProperties { canFocus = false },
                        onClick = { onTextFieldValueChange(actions.listNumbered.applyAction(textFieldValue)) },
                    ) {
                        Icon(Icons.Default.FormatListNumbered, contentDescription = "List numbered")
                    }
                    IconButton(
                        modifier = Modifier.focusProperties { canFocus = false },
                        onClick = { onTextFieldValueChange(actions.quote.applyAction(textFieldValue)) },
                    ) {
                        Icon(Icons.Default.FormatQuote, contentDescription = "Quote")
                    }
                    IconButton(
                        modifier = Modifier.focusProperties { canFocus = false },
                        onClick = { onTextFieldValueChange(actions.link.applyAction(textFieldValue)) },
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Link")
                    }
                    IconButton(
                        modifier = Modifier.focusProperties { canFocus = false },
                        onClick = { onTextFieldValueChange(actions.inlineCode.applyAction(textFieldValue)) },
                    ) {
                        Icon(Icons.Default.Code, contentDescription = "Inline code")
                    }
                    IconButton(
                        modifier = Modifier.focusProperties { canFocus = false },
                        onClick = { onTextFieldValueChange(actions.codeBlock.applyAction(textFieldValue)) },
                    ) {
                        Icon(painterResource("code_blocks.xml"), contentDescription = "Code block")
                    }
                }
            }
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
                    PlainTextFileEditor(textFieldValue, FontFamily.Monospace, onTextFieldValueChange)
                }
            }
            val html = when (fileType) {
                MarkupTextFileType.Html -> textFieldValue.text
                MarkupTextFileType.Markdown -> rememberSaveable(textFieldValue.text) {
                    val flavour = org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor()
                    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(textFieldValue.text)
                    HtmlGenerator(textFieldValue.text, parsedTree, flavour).generateHtml()
                }
            }


            Box(
                modifier = Modifier.width(secondWidth).align(Alignment.CenterEnd)
            ) {
                androidx.compose.animation.AnimatedVisibility(secondWidth != 0.dp, enter = fadeIn(), exit = fadeOut()) {
                    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
                    Box(
                        Modifier
                            .padding(8.dp)
                            .background(backgroundColor, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        HtmlView(
                            html = html,
                            backgroundColor = backgroundColor,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerPopup(offset: IntOffset, onColorChosen: (Color?) -> Unit) {
    val initialColor = LocalContentColor.current
    val (currentColor, onCurrentColorChange) = remember { mutableStateOf(HsvColor.from(initialColor)) }
    NativeDialog(title = "Pick color", onDismissRequest = { onColorChosen(null) }, size = DpSize(320.dp, 420.dp)) {
        Surface(
            modifier = Modifier,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(IntrinsicSize.Min),
            ) {
                ClassicColorPicker(
                    color = currentColor,
                    onColorChanged = onCurrentColorChange,
                    modifier = Modifier.padding(16.dp).size(300.dp, 300.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = { onColorChosen(currentColor.toColor()) }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                    IconButton(onClick = { onColorChosen(null) }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            }
        }
    }
}

fun interface EditorAction {
    fun applyAction(textFieldValue: TextFieldValue): TextFieldValue
}

fun interface EditorActionWithParameter<T> {
    fun applyAction(textFieldValue: TextFieldValue, parameter: T): TextFieldValue
}

data class EditorActions(
    val bold: EditorAction,
    val italic: EditorAction,
    val underline: EditorAction,
    val strikeThrough: EditorAction,
    val overline: EditorAction,
    val foregroundColor: EditorActionWithParameter<Color>,
    val backgroundColor: EditorActionWithParameter<Color>,
    val listBulleted: EditorAction,
    val listNumbered: EditorAction,
    val quote: EditorAction,
    val link: EditorAction,
    val inlineCode: EditorAction,
    val codeBlock: EditorAction,
)

private fun Color.toArgbString() = "rgba(${red * 256}, ${green * 256}, ${blue * 256}, ${alpha * 256})"

val htmlActions = EditorActions(
    bold = { it.wrap("<b>", "</b>") },
    italic = { it.wrap("<i>", "</i>") },
    underline = { it.wrap("<u>", "</u>") },
    strikeThrough = { it.wrap("<s>", "</s>") },
    overline = { it.wrap("<span style=\"text-decoration-line: overline;\">", "</span>") },
    foregroundColor = { text, color -> text.wrap("<span style=\"color: ${color.toArgbString()};\">", "</span>") },
    backgroundColor = { text, color ->
        val start = "<span style=\"background-color: ${color.toArgbString()};\">"
        text.wrap(start, "</span>")
    },
    listBulleted = { it.wrapList("<ul>\n", { "  <li>" }, { "</li>\n" }, "</ul>") },
    listNumbered = { it.wrapList("<ol>\n", { "  <li>" }, { "</li>\n" }, "</ol>") },
    quote = { it.wrap("<blockquote>", "</blockquote>") },
    link = { it.wrap("<a href=\"${it.getSelectedText()}\">", "</a>") },
    inlineCode = { it.wrap("<code>", "</code>") },
    codeBlock = { it.wrap("<pre>", "</pre>") },
)

private fun TextFieldValue.charactersBeforeSelectionInTheLine() =
    ((selection.min - 1) downTo 0)
        .asSequence()
        .map { text[it] }
        .takeWhile { it != '\n' && it != '\r' }

val markdownActions = htmlActions.copy(
    bold = { it.wrap("**", "**") },
    italic = { it.wrap("*", "*") },
    quote = { text ->
        val extraLinesBefore = linesToAddBeforeBlock(text, setOf('>'))
        val extraLinesAfter = linesToAddAfterBlock(text, setOf('>'))
        text.wrap("${"\n".repeat(extraLinesBefore)}> ", "\n".repeat(extraLinesAfter))
    },
    listBulleted = { text ->
        val line = text.charactersBeforeSelectionInTheLine().toList().asReversed().joinToString("")
        val putNewLine = line.any { !it.isWhitespace() && it != '*' }
        val spacesBefore =
            if (putNewLine) "" else line.map { if (it.isWhitespace()) it else ' ' }.joinToString("")
        text.wrapList(
            listStart = if (putNewLine) "\n\n" else "",
            makeItemStart = { "${if (it != 0) spacesBefore else ""}* " },
            makeItemEnd = { "\n" },
            listEnd = ""
        )
    },
    listNumbered = { text ->
        val line = text.charactersBeforeSelectionInTheLine().toList().asReversed().joinToString("")
        val putNewLine = !line.isValidNumberedListPrefix()
        val spacesBefore =
            if (putNewLine) "" else line.map { if (it.isWhitespace()) it else ' ' }.joinToString("")
        text.wrapList(
            listStart = if (putNewLine) "\n\n" else "",
            makeItemStart = { "${if (it != 0) spacesBefore else ""}${it + 1}. " },
            makeItemEnd = { "\n" },
            listEnd = ""
        )
    },
    link = { it.wrap("[", "](${it.getSelectedText()})") },
    inlineCode = { it.wrap("`", "`") },
    codeBlock = { text ->
        val extraLinesBefore = linesToAddBeforeBlock(text, emptySet()) >= 2
        val extraLinesAfter = linesToAddAfterBlock(text, emptySet()) >= 2
        text.wrap(
            start = (if (extraLinesBefore) "\n" else "") + "```\n",
            end = "\n```" + if (extraLinesAfter) "\n" else "",
        )
    },
)

private enum class ValidNumberedListPrefixState { AfterSpace, AfterNumber, AfterDot }

private fun String.isValidNumberedListPrefix(): Boolean {
    var state = ValidNumberedListPrefixState.AfterSpace
    for (c in this) {
        if (c.isWhitespace()) {
            if (state == ValidNumberedListPrefixState.AfterNumber) return false
            state = ValidNumberedListPrefixState.AfterSpace
        } else if (c.isDigit()) {
            if (state == ValidNumberedListPrefixState.AfterDot) return false
            state = ValidNumberedListPrefixState.AfterNumber
        } else if (c == '.') {
            if (state != ValidNumberedListPrefixState.AfterNumber) return false
            state = ValidNumberedListPrefixState.AfterDot
        } else {
            return false
        }
    }
    return state == ValidNumberedListPrefixState.AfterSpace
}

private fun linesToAddBeforeBlock(text: TextFieldValue, additionalSymbols: Set<Char>): Int {
    var i = text.selection.min - 1
    while (i >= 0) {
        val char = text.text[i]
        when {
            char == '\n' && i > 0 && text.text[i - 1] == '\r' -> {
                i--; break
            }

            char == '\n' || char == '\r' -> break
            char.isWhitespace() -> i--
            char in additionalSymbols -> i--
            else -> return 2
        }
    }
    if (i == 0) return 0
    i--
    while (i >= 0) {
        val char = text.text[i]
        when {
            char == '\n' || char == '\r' -> return 0
            char.isWhitespace() -> i--
            char in additionalSymbols -> i--
            else -> return 1
        }
    }
    return 0
}

private fun linesToAddAfterBlock(text: TextFieldValue, additionalSymbols: Set<Char>): Int {
    var i = text.selection.max
    while (i < text.text.length) {
        val char = text.text[i]
        when {
            char == '\r' && i < text.text.lastIndex && text.text[i + 1] == '\n' -> {
                i++; break
            }

            char == '\n' || char == '\r' -> break
            char.isWhitespace() -> i++
            char in additionalSymbols -> i++
            else -> return 2
        }
    }
    if (i == text.text.length) return 0
    i++
    while (i < text.text.length) {
        val char = text.text[i]
        when {
            char == '\n' || char == '\r' -> return 0
            char.isWhitespace() -> i++
            char in additionalSymbols -> i++
            else -> return 1
        }
    }
    return 0
}

private fun TextFieldValue.wrap(start: String, end: String): TextFieldValue {
    val newText = buildAnnotatedString {
        append(this@wrap.annotatedString, 0, this@wrap.selection.min)
        append(start)
        append(this@wrap.annotatedString, this@wrap.selection.min, this@wrap.selection.max)
        append(end)
        append(this@wrap.annotatedString, this@wrap.selection.max, this@wrap.annotatedString.length)
    }

    fun newIndex(index: Int) = when {
        index < selection.min -> index
        index <= selection.max -> index + start.length
        else -> index + start.length + end.length
    }

    val newSelection = TextRange(newIndex(selection.start), newIndex(selection.end))
    val newComposition = composition?.let { TextRange(newIndex(it.start), newIndex(it.end)) }
    return TextFieldValue(newText, newSelection, newComposition)
}

private fun TextFieldValue.wrapList(
    listStart: String,
    makeItemStart: (index: Int) -> String,
    makeItemEnd: (index: Int) -> String,
    listEnd: String
): TextFieldValue {
    var newSelectionStart = selection.start
    var newSelectionEnd = selection.end
    var newCompositionStart = composition?.start
    var newCompositionEnd = composition?.end
    val text = buildAnnotatedString {
        fun increment(oldIndex: Int, length: Int, removedCharsCount: Int = 0) {
            if (selection.start >= oldIndex) newSelectionStart += length - removedCharsCount
            if (selection.end >= oldIndex) newSelectionEnd += length - removedCharsCount
            if (composition != null) {
                if (composition!!.start >= oldIndex) newCompositionStart =
                    newCompositionStart!! + length - removedCharsCount
                if (composition!!.end >= oldIndex) newCompositionEnd = newCompositionEnd!! + length - removedCharsCount
            }
        }
        append(this@wrapList.annotatedString, 0, this@wrapList.selection.min)

        append(listStart)
        increment(selection.min, listStart.length)

        var isDoubleCharNewLine = false
        var currentLineStart = selection.min
        var elementIndex = 0

        fun addLine(currentIndex: Int, removedCharsCount: Int = 0) {
            val itemStart = makeItemStart(elementIndex)
            val itemEnd = makeItemEnd(elementIndex)

            append(itemStart)
            increment(currentLineStart, itemStart.length)

            append(this@wrapList.annotatedString, currentLineStart, currentIndex)

            append(itemEnd)
            increment(currentIndex + 1, itemEnd.length, removedCharsCount)

            currentLineStart = currentIndex + 1
        }

        for (i in selection.min..selection.max) {
            val c = if (i < this@wrapList.text.length) this@wrapList.annotatedString[i] else break
            if (c == '\n' && isDoubleCharNewLine) {
                isDoubleCharNewLine = false
                currentLineStart++
                increment(i, 0, removedCharsCount = 1)
                continue
            }

            if (c == '\n' || c == '\r') {
                addLine(i, removedCharsCount = 1)
                elementIndex++
            }

            isDoubleCharNewLine = false
            if (c == '\r') {
                isDoubleCharNewLine = true
            }
        }
        if (currentLineStart <= selection.max) {
            addLine(selection.max)
        }

        append(listEnd)
        increment(selection.max + 1, listEnd.length)

        append(this@wrapList.annotatedString, this@wrapList.selection.max, this@wrapList.annotatedString.length)
    }

    return TextFieldValue(
        annotatedString = text,
        selection = TextRange(newSelectionStart, newSelectionEnd),
        composition = newCompositionStart?.let { TextRange(it, newCompositionEnd!!) },
    )
}

@Composable
expect fun HtmlView(html: String, backgroundColor: Color, modifier: Modifier)
