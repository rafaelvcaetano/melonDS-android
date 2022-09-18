package me.magnum.melonds.domain.model.dsinand

enum class ImportTitleResult {
    SUCCESS,
    NAND_NOT_OPEN,
    ERROR_OPENING_FILE,
    NOT_DSIWARE_TITLE,
    TITLE_ALREADY_IMPORTED,
    INSATLL_FAILED,
    UNKNOWN,
}