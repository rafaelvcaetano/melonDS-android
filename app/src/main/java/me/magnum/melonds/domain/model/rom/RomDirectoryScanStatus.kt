package me.magnum.melonds.domain.model.rom

import android.net.Uri

data class RomDirectoryScanStatus(
    val directoryUri: Uri,
    val lastScanTimestamp: Long?,
    val result: ScanResult
) {
    enum class ScanResult {
        UPDATED,
        UNCHANGED,
        NOT_SCANNED
    }
}
