package me.magnum.melonds.domain.model

data class CheatImportProgress(val status: CheatImportStatus, val progress: Float, val ongoingItemName: String?) {
    enum class CheatImportStatus {
        NOT_IMPORTING,
        STARTING,
        ONGOING,
        FINISHED,
        FAILED
    }
}