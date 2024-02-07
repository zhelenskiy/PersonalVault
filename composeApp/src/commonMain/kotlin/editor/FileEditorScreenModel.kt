package editor

import cafe.adriel.voyager.core.model.ScreenModel
import common.EncryptedSpaceInfo
import common.File
import common.FileSystemItem
import common.SpaceStructure
import crypto.CryptoProvider
import crypto.PrivateKey
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import org.kodein.di.DI
import org.kodein.di.bindFactory
import org.kodein.di.instance
import repositories.SpacesRepository
import kotlin.coroutines.EmptyCoroutineContext


val fileEditorModule = DI.Module("FileEditorScreenModel") {
    bindFactory { info: FileEditorScreenInfo -> FileEditorScreenModel(di, info) }
}

data class FileEditorScreenInfo(
    val spaceIndex: Int,
    val privateKey: PrivateKey,
    val fileId: FileSystemItem.FileId
)

@OptIn(ExperimentalCoroutinesApi::class)
class FileEditorScreenModel(di: DI, editorScreenInfo: FileEditorScreenInfo) : ScreenModel {
    private val spaceIndex = editorScreenInfo.spaceIndex
    private val privateKey = editorScreenInfo.privateKey
    private val fileId = editorScreenInfo.fileId
    private val cryptoProvider by di.instance<CryptoProvider>()
    private val spacesRepository by di.instance<SpacesRepository>()
    private val spaceSavingScope = CoroutineScope(EmptyCoroutineContext)

    private val savingCount = MutableStateFlow(0)

    private val fileSystemFlowImpl = MutableStateFlow<File?>(null).apply {
        spaceSavingScope.launch {
            value = getFile()
        }
        spaceSavingScope.launch {
            collectLatest { newFile ->
                if (newFile != null) {
                    try {
                        savingCount.value++
                        updateFileInRepository(newFile)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    } finally {
                        savingCount.value--
                    }
                }
            }
        }
    }

    val isSaving = savingCount
        .mapLatest { it > 0 }
        .stateIn(spaceSavingScope, WhileSubscribed(), false)

    private fun updateFileInRepository(newFile: File?) {
        spacesRepository.updateSpaces {
            val oldSpaceEncrypted = it[spaceIndex]
            val oldSpace = SpaceStructure.fromEncryptedBytes(cryptoProvider, oldSpaceEncrypted, privateKey)
            val spaceName = it[spaceIndex].name
            val newFileSystem = SpaceStructure(oldSpace.fileStructure, oldSpace.files.put(fileId, newFile!!))
            val newSpace = newFileSystem.toEncryptedBytes(spaceName, privateKey, cryptoProvider)
            it.set(spaceIndex, newSpace)
        }
    }

    val fileSystemFlow: StateFlow<File?> = fileSystemFlowImpl

    private suspend fun convertSpacesToFile(spaces: PersistentList<EncryptedSpaceInfo>): File {
        val space = spaces[spaceIndex]
        return withContext(Dispatchers.Default) {
            SpaceStructure.fromEncryptedBytes(cryptoProvider, space, privateKey).files[fileId]
                ?: error("file not found")
        }
    }

    private suspend fun getFile(): File = convertSpacesToFile(spacesRepository.getCurrentSpaces())

    fun setFile(newFile: File) {
        fileSystemFlowImpl.value = newFile
    }
}
