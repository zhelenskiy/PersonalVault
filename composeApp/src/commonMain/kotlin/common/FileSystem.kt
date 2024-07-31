package common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import crypto.CryptoProvider
import crypto.PrivateKey
import kotlinx.collections.immutable.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.vectorResource
import personalvault.composeapp.generated.resources.Res
import personalvault.composeapp.generated.resources.markdown
import personalvault.composeapp.generated.resources.unknown_document

@Serializable
sealed class FileType {
    abstract val extension: String?
}

private val json = Json { ignoreUnknownKeys = true }

fun getFileTypeByExtension(extension: String?) = when (extension) {
    "html" -> MarkupTextFileType.Html
    "htm" -> MarkupTextFileType.Htm
    "md" -> MarkupTextFileType.Markdown
    "txt" -> TextFileType.PlainText
    null -> BinaryFileType(null)
    in sourceCodeExtensions -> TextFileType.SourceCodeFileType(extension)
    else -> BinaryFileType(extension)
}

@Serializable
data class BinaryFileType(override val extension: String?) : FileType()

@Serializable
sealed class TextFileType : FileType() {
    val name = this::class.simpleName!!

    @Serializable
    data object PlainText : TextFileType() {
        override val extension: String get() = "txt"
    }

    @Serializable
    data class SourceCodeFileType(override val extension: String) : TextFileType()
}

@Serializable
sealed class MarkupTextFileType : TextFileType() {

    @Serializable
    data object Markdown : MarkupTextFileType() {
        override val extension: String get() = "md"
    }

    @Serializable
    data object Html : MarkupTextFileType() {
        override val extension: String get() = "html"
    }

    @Serializable
    data object Htm : MarkupTextFileType() {
        override val extension: String get() = "htm"
    }
}


@Serializable
sealed class File {
    abstract val name: String

    @SerialName("fileType")
    abstract val type: FileType
    abstract val content: ByteArray
    abstract fun makeFileContent(): ByteArray
    abstract fun convert(fileType: FileType): File

    companion object {
        operator fun invoke(name: String, type: FileType, content: ByteArray): File = when (type) {
            is TextFileType -> TextFile(name, type, content)
            is BinaryFileType -> BinaryFile(name, type, content)
        }

        operator fun invoke(name: String, type: FileType): File = when (type) {
            is TextFileType -> TextFile(name, type)
            is BinaryFileType -> BinaryFile(name, type, byteArrayOf())
        }


        fun createFromRealFile(baseName: String, extension: String?, content: ByteArray): File = when (val type = getFileTypeByExtension(extension)) {
            is TextFileType -> TextFile(baseName, type, text = content.decodeToString())
            is BinaryFileType -> BinaryFile(baseName, type, content)
        }
    }
}

@Serializable
class BinaryFile(override val name: String, @SerialName("fileType") override val type: BinaryFileType, override val content: ByteArray) : File() {
    override fun makeFileContent(): ByteArray = content
    override fun convert(fileType: FileType): File = when (fileType) {
        is BinaryFileType -> BinaryFile(name, fileType, content)
        is TextFileType -> TextFile(name, fileType, text = content.decodeToString())
    }
}

@Serializable
sealed class TextFile : File() {
    @SerialName("fileType")
    abstract override val type: TextFileType
    abstract val text: String

    override fun makeFileContent(): ByteArray = text.encodeToByteArray()

    override fun convert(fileType: FileType): File = when (fileType) {
        is BinaryFileType -> BinaryFile(name, fileType, makeFileContent())
        is TextFileType -> TextFile(name, fileType, text = text)
    }

    companion object {
        operator fun invoke(name: String, type: TextFileType, content: ByteArray): TextFile = when (type) {
            is MarkupTextFileType -> MarkupTextFile(name, type, content)
            TextFileType.PlainText -> PlainTextFile(name, content)
            is TextFileType.SourceCodeFileType -> SourceCodeFile(name, type, content)
        }

        operator fun invoke(name: String, type: TextFileType): TextFile = when (type) {
            is MarkupTextFileType -> MarkupTextFile(name, type)
            TextFileType.PlainText -> PlainTextFile(name)
            is TextFileType.SourceCodeFileType -> SourceCodeFile(name, type)
        }

        operator fun invoke(name: String, type: TextFileType, text: String): TextFile = when (type) {
            is MarkupTextFileType -> MarkupTextFile(name, type, text = text)
            TextFileType.PlainText -> PlainTextFile(name, text = text)
            is TextFileType.SourceCodeFileType -> SourceCodeFile(name, type, text = text)
        }
    }
}

@Serializable
class SourceCodeFile private constructor(
    override val name: String,
    override val content: ByteArray,
    override val text: String,
    @SerialName("fileType")
    override val type: TextFileType.SourceCodeFileType
) : TextFile() {
    constructor(name: String, type: TextFileType.SourceCodeFileType, text: String = "") : this(name, text.encodeToByteArray(), text, type)
    constructor(name: String, type: TextFileType.SourceCodeFileType, content: ByteArray) : this(name, content, content.decodeToString(), type)
}

@Serializable
enum class MarkedUpTextFileEditorMode { Source, Both, Preview }

@Serializable
class MarkupTextFile private constructor(
    override val name: String,
    @SerialName("fileType") override val type: MarkupTextFileType,
    override val content: ByteArray,
    private val state: MarkupTextFileState,
) : TextFile() {
    override val text: String get() = state.text
    val editorMode: MarkedUpTextFileEditorMode get() = state.editorMode

    override fun convert(fileType: FileType): File = when (fileType) {
        is MarkupTextFileType -> MarkupTextFile(name, fileType, text, editorMode)
        else -> super.convert(fileType)
    }

    constructor(
        name: String,
        type: MarkupTextFileType,
        text: String = "",
        editorMode: MarkedUpTextFileEditorMode = MarkedUpTextFileEditorMode.Source
    ) : this(name, type, MarkupTextFileState(text, editorMode).toByteArray(), MarkupTextFileState(text, editorMode))

    constructor(name: String, type: MarkupTextFileType, content: ByteArray) :
        this(name, type, content, MarkupTextFileState.fromByteArray(content))

    @Serializable
    private data class MarkupTextFileState(
        val text: String,
        val editorMode: MarkedUpTextFileEditorMode
    ) {
        fun toByteArray() = json.encodeToString(this).toByteArray()

        companion object {
            fun fromByteArray(bytes: ByteArray): MarkupTextFileState = json.decodeFromString(bytes.decodeToString())
        }
    }
}

@Serializable
class PlainTextFile private constructor(
    override val name: String,
    override val content: ByteArray,
    override val text: String
) : TextFile() {
    constructor(name: String, text: String = "") : this(name, text.encodeToByteArray(), text)
    constructor(name: String, content: ByteArray) : this(name, content, content.decodeToString())

    @SerialName("fileType")
    override val type: TextFileType get() = TextFileType.PlainText
}

private object InlineFileIdSerializer : KSerializer<FileSystemItem.FileId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FileId", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): FileSystemItem.FileId = FileSystemItem.FileId(decoder.decodeLong())
    override fun serialize(encoder: Encoder, value: FileSystemItem.FileId) = encoder.encodeLong(value.id)
}

@Serializable
sealed class FileSystemItem : JavaSerializable {
    @Serializable
    sealed class RegularFileSystemItem : FileSystemItem(), JavaSerializable

    @Serializable
    data class FileId(val id: Long) : RegularFileSystemItem(), JavaSerializable

    @Serializable
    class Directory(
        val name: String,
        @Serializable(with = PersistentListSerializer::class)
        val children: PersistentList<RegularFileSystemItem>,
    ) : RegularFileSystemItem()

    @Serializable
    class Root(
        @Serializable(with = PersistentListSerializer::class)
        val children: PersistentList<RegularFileSystemItem>
    ) : FileSystemItem()
}

private val sourceCodeExtensions = setOf(
    "c", "cc", "cpp", "cxx", "h", "hh", "hpp", "hxx",
    "java",
    "py",
    "js",
    "ts",
    "jsx",
    "tsx",
    "php",
    "rb",
    "cs",
    "swift",
    "go",
    "rs",
    "dart",
    "kt", "kts",
    "scala",
    "pl", "pm",
    "lua",
    "r",
    "sh", "bash", "zsh",
    "ps1",
    "html", "htm",
    "css",
    "sass", "scss",
    "less",
    "xml",
    "yaml", "yml",
    "json",
    "sql",
    "groovy",
    "m", "mm",
    "vb",
    "vbs",
    "f", "for", "f90",
    "hs",
    "lhs",
    "erl", "hrl",
    "ex", "exs",
    "clj", "cljs",
    "coffee",
    "tcl",
    "rkt",
    "nim",
    "cr",
    "ada",
    "adb",
    "ads",
    "d",
    "pas", "pp",
    "lisp", "lsp",
    "scm",
    "jl",
    "mjs",
    "rpy",
    "hx",
    "cshtml",
    "aspx",
    "jsp",
    "asp",
    "erl",
    "ml",
    "mli",
    "fs",
    "fsi",
    "fsx",
    "fsi",
    "purs",
    "elm",
    "idr",
    "eex",
    "leex",
    "svelte",
    "vue",
    "sol",
    "jl",
    "sql",
    "prc",
    "cls",
    "sfd",
    "tcc",
    "chpl",
    "au3",
    "asm",
    "s",
    "psql",
    "pgsql",
    "pp",
    "cgi",
    "awk",
    "sed"
)

private val videoFileExtensions = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "mpg", "mpeg", "m4v", "3gp", "3g2", "mxf", "ogv", "ts",
    "mts", "m2ts", "vob", "rm", "rmvb", "divx", "f4v", "swf"
)

private val audioFileExtensions = setOf(
    "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "alac", "aiff", "ape", "amr", "mid", "midi", "oga",
    "opus", "ra", "rm", "tta"
)

private val imageFileExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp", "svg", "heif",
    "heic", "raw", "cr2", "nef", "orf", "sr2", "arw", "dng", "rw2", "ico", "psd"
)


@Composable
fun FileType.icon() = when (this) {
    is TextFileType.PlainText -> Icon(Icons.AutoMirrored.Filled.TextSnippet, contentDescription = "Plain text")
    is MarkupTextFileType.Markdown -> Icon(vectorResource(Res.drawable.markdown), contentDescription = name)
    is MarkupTextFileType.Html -> Icon(Icons.Default.Html, contentDescription = name)
    is MarkupTextFileType.Htm -> Icon(Icons.Default.Html, contentDescription = name)
    is TextFileType.SourceCodeFileType -> Icon(Icons.Default.Code, "$extension file")
    is BinaryFileType -> when (extension) {
        in sourceCodeExtensions -> Icon(Icons.Default.Code, "${extension ?: ""} file")
        in videoFileExtensions -> Icon(Icons.Default.VideoFile, "${extension ?: ""} file")
        in audioFileExtensions -> Icon(Icons.Default.AudioFile, "${extension ?: ""} file")
        in imageFileExtensions -> Icon(Icons.Default.Image, "${extension ?: ""} file")
        else -> Icon(vectorResource(Res.drawable.unknown_document), "${extension ?: ""} file")
    }
}

@Serializable
class SpaceStructure(
    val fileStructure: FileSystemItem.Root,
    @Serializable(with = PersistentMapSerializer::class)
    val files: PersistentMap<@Serializable(with = InlineFileIdSerializer::class) FileSystemItem.FileId, File>
) {
    constructor() : this(FileSystemItem.Root(persistentListOf()), persistentMapOf())

    operator fun get(fileId: FileSystemItem.FileId) = files[fileId]
    private fun toBytes() = json.encodeToString(this).encodeToByteArray()
    suspend fun toDecryptedBytes(name: String, privateKey: PrivateKey, provider: CryptoProvider) =
        DecryptedSpaceInfo.fromDecryptedData(
            name = name,
            privateKey = privateKey,
            data = toBytes(),
            provider = provider
        )

    suspend fun toEncryptedBytes(name: String, privateKey: PrivateKey, provider: CryptoProvider) =
        toDecryptedBytes(name, privateKey, provider).toEncryptedSpaceInfo()

    companion object {
        private fun fromBytes(bytes: ByteArray) = json.decodeFromString<SpaceStructure>(bytes.decodeToString())
        fun fromDecryptedBytes(decryptedSpaceInfo: DecryptedSpaceInfo) = fromBytes(decryptedSpaceInfo.decryptedData)
        suspend fun fromEncryptedBytes(
            cryptoProvider: CryptoProvider,
            encryptedSpaceInfo: EncryptedSpaceInfo,
            privateKey: PrivateKey
        ) =
            fromDecryptedBytes(
                DecryptedSpaceInfo(
                    encryptedSpaceInfo.name,
                    privateKey,
                    encryptedSpaceInfo.encryptedData,
                    cryptoProvider
                )
            )
    }
}
