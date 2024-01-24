package me.magnum.melonds.domain.model.appupdate

import android.net.Uri
import me.magnum.melonds.domain.model.Version
import java.time.Instant

data class AppUpdate(
    val type: Type,
    val id: Long,
    val downloadUri: Uri,
    val newVersion: Version,
    val description: String,
    val binarySize: Long,
    val updateDate: Instant,
) {

    enum class Type {
        PRODUCTION,
        NIGHTLY,
    }
}