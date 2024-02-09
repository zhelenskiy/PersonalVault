package editor

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import common.*
import crypto.CryptoProvider
import crypto.PrivateKey
import kotlinx.coroutines.flow.*
import org.kodein.di.DI
import org.kodein.di.bindFactory
import org.kodein.di.instance
import repositories.OutdatedData
import repositories.SpacesRepository


val fileEditorModule = DI.Module("FileEditorScreenModel") {
    bindFactory { info: FileEditorScreenInfo -> FileEditorScreenModel(di, info) }
}

data class FileEditorScreenInfo(
    val spaceIndex: Int,
    val privateKey: PrivateKey,
    val fileId: FileSystemItem.FileId
)

class FileEditorScreenModel(di: DI, editorScreenInfo: FileEditorScreenInfo) : ScreenModel {
    private val spaceIndex = editorScreenInfo.spaceIndex
    private val privateKey = editorScreenInfo.privateKey
    private val fileId = editorScreenInfo.fileId
    private val cryptoProvider by di.instance<CryptoProvider>()
    private val spacesRepository by di.instance<SpacesRepository>()
    private val savingScope get() = screenModelScope

    @OptIn(OutdatedData::class)
    private val spacesData = makeSpacesDataSynchronizer(savingScope, spacesRepository)

    private val spaceStructureData = makeSpaceStructureSyncData(
        spacesData, spaceIndex, privateKey, cryptoProvider
    )

    private val fileStructureData = spaceStructureData.map(
        transformForward = { it?.get(fileId) },
        transformBackward = {
            if (this == null || it == null) null else SpaceStructure(fileStructure, files.put(fileId, it))
        }
    )

    val isSaving = fileStructureData.isLoading

    val fileSystemFlow: StateFlow<File?> = fileStructureData.values

    fun setFile(version: Long, newFile: File) {
        fileStructureData.setValue(Versioned(version, newFile))
    }

    fun getNewVersionNumber() = spacesRepository.getNewVersionNumber()
}
