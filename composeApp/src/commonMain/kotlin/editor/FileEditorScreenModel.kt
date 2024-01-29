package editor

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.kodein.di.DI
import org.kodein.di.bindProvider


val fileEditorModule = DI.Module("FileEditorScreenModel") {
    bindProvider { FileEditorScreenModel(di) }
}

class FileEditorScreenModel(di: DI) : ScreenModel {
//    private val fileFlowImpl = MutableStateFlow()
}
