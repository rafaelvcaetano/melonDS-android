package me.magnum.melonds.ui.dsiwaremanager.model

sealed class ImportExportDSiWareTitleFileEvent {
    data class ImportSuccess(val fileName: String) : ImportExportDSiWareTitleFileEvent()
    data object ImportError : ImportExportDSiWareTitleFileEvent()
    data class ExportSuccess(val fileName: String) : ImportExportDSiWareTitleFileEvent()
    data object ExportError : ImportExportDSiWareTitleFileEvent()
}