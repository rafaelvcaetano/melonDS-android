package me.magnum.melonds.migrations.legacy

import android.net.Uri
import me.magnum.melonds.domain.model.RomConfig
import java.util.*

/**
 * ROM model used from app version 22.
 */
data class Rom22(
    val name: String,
    val fileName: String,
    val uri: Uri,
    val parentTreeUri: Uri,
    var config: RomConfig,
    var lastPlayed: Date? = null,
    val isDsiWareTitle: Boolean,
)
