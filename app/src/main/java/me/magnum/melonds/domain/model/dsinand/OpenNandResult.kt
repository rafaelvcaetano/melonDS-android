package me.magnum.melonds.domain.model.dsinand

enum class OpenNandResult {
    SUCCESS,
    NAND_ALREADY_OPEN,
    BIOS7_NOT_FOUND,
    NAND_OPEN_FAILED,
    INVALID_DSI_SETUP,
    UNKNOWN,
}