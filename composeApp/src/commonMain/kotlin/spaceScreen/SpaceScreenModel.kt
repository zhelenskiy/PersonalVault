package spaceScreen

import cafe.adriel.voyager.core.model.ScreenModel
import common.FileSystemItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.kodein.di.DI
import org.kodein.di.bindProvider


val spaceModule = DI.Module("SpaceScreenModel") {
    bindProvider { SpaceScreenModel(di) }
}

class SpaceScreenModel(di: DI) : ScreenModel {
    private val spaceFileSystemFlowImpl = MutableStateFlow(FileSystemItem.Root(persistentListOf()))

    val spaceFileSystemFlow: StateFlow<FileSystemItem.Root> get() = spaceFileSystemFlowImpl

    fun setFileSystem(newFileSystem: FileSystemItem.Root) {
        spaceFileSystemFlowImpl.value = newFileSystem
    }
}