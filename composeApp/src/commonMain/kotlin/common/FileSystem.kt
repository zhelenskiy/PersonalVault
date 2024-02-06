package common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import crypto.CryptoProvider
import crypto.PrivateKey
import kotlinx.collections.immutable.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

class PersistentListSerializer<T>(private val serializer: KSerializer<T>) : KSerializer<PersistentList<T>> {
    private val delegate = ListSerializer(serializer)

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("PersistentList", delegate.descriptor)

    override fun deserialize(decoder: Decoder): PersistentList<T> {
        return decoder.decodeSerializableValue(delegate).toPersistentList()
    }

    override fun serialize(encoder: Encoder, value: PersistentList<T>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

class PersistentMapSerializer<K, V>(
    private val keySerializer: KSerializer<K>,
    private val valueSerializer: KSerializer<V>
) : KSerializer<PersistentMap<K, V>> {
    private val delegate = MapSerializer(keySerializer, valueSerializer)

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("PersistentMap", delegate.descriptor)

    override fun deserialize(decoder: Decoder): PersistentMap<K, V> {
        return decoder.decodeSerializableValue(delegate).toPersistentMap()
    }

    override fun serialize(encoder: Encoder, value: PersistentMap<K, V>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

@Serializable
sealed class FileType {
    val name = this::class.simpleName!!

    companion object {
        val entries: List<FileType> by lazy { TextFileType.entries }
    }
}

@Serializable
sealed class TextFileType : FileType() {
    @Serializable
    data object PlainText : TextFileType()
    companion object {
        val entries: List<TextFileType> by lazy { listOf(PlainText) + MarkupTextFileType.entries }
    }
}

@Serializable
sealed class MarkupTextFileType : TextFileType() {

    @Serializable
    data object Markdown : MarkupTextFileType()

    @Serializable
    data object Html : MarkupTextFileType()

    companion object {
        val entries: List<MarkupTextFileType> = listOf(Markdown, Html)
    }
}


@Serializable
sealed class File {
    abstract val name: String

    @SerialName("fileType")
    abstract val type: FileType
    abstract val content: ByteArray

    companion object {
        operator fun invoke(name: String, type: FileType, content: ByteArray): File = when (type) {
            is TextFileType -> TextFile(name, type, content)
        }

        operator fun invoke(name: String, type: FileType): File = when (type) {
            is TextFileType -> TextFile(name, type)
        }
    }
}

@Serializable
sealed class TextFile : File() {
    @SerialName("fileType")
    abstract override val type: TextFileType
    abstract val text: String

    companion object {
        operator fun invoke(name: String, type: TextFileType, content: ByteArray): TextFile = when (type) {
            is MarkupTextFileType -> MarkupTextFile(name, type, content)
            TextFileType.PlainText -> PlainTextFile(name, content)
        }

        operator fun invoke(name: String, type: TextFileType): TextFile = when (type) {
            is MarkupTextFileType -> MarkupTextFile(name, type)
            TextFileType.PlainText -> PlainTextFile(name)
        }
    }
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
        fun toByteArray() = Json.encodeToString(this).toByteArray()

        companion object {
            fun fromByteArray(bytes: ByteArray): MarkupTextFileState = Json.decodeFromString(bytes.decodeToString())
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
        val isCollapsed: Boolean
    ) : RegularFileSystemItem()

    @Serializable
    class Root(
        @Serializable(with = PersistentListSerializer::class)
        val children: PersistentList<RegularFileSystemItem>
    ) : FileSystemItem()
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun FileType.icon() = when (this) {
    TextFileType.PlainText -> Icon(Icons.Default.TextSnippet, contentDescription = "Plain text")
    MarkupTextFileType.Markdown -> Icon(painterResource("markdown.xml"), contentDescription = name)
    MarkupTextFileType.Html -> Icon(Icons.Default.Html, contentDescription = name)
}

@Serializable
class SpaceStructure(
    val fileStructure: FileSystemItem.Root,
    @Serializable(with = PersistentMapSerializer::class)
    val files: PersistentMap<@Serializable(with = InlineFileIdSerializer::class) FileSystemItem.FileId, File>
) {
    constructor() : this(FileSystemItem.Root(persistentListOf()), persistentMapOf())

    operator fun get(fileId: FileSystemItem.FileId) = files[fileId]
    private fun toBytes() = Json.encodeToString(this).encodeToByteArray()
    fun toDecryptedBytes(name: String, privateKey: PrivateKey, provider: CryptoProvider) =
        DecryptedSpaceInfo.fromDecryptedData(
            name = name,
            privateKey = privateKey,
            data = toBytes(),
            provider = provider
        )

    fun toEncryptedBytes(name: String, privateKey: PrivateKey, provider: CryptoProvider) =
        toDecryptedBytes(name, privateKey, provider).toEncryptedSpaceInfo()

    companion object {
        private fun fromBytes(bytes: ByteArray) = Json.decodeFromString<SpaceStructure>(bytes.decodeToString())
        fun fromDecryptedBytes(decryptedSpaceInfo: DecryptedSpaceInfo) = fromBytes(decryptedSpaceInfo.decryptedData)
        fun fromEncryptedBytes(
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
