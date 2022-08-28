package me.magnum.melonds.migrations.legacy

import android.net.Uri
import me.magnum.melonds.domain.model.RomConfig
import java.util.*

/**
 * ROM model used until app version 21.
 */
data class Rom21(
    val name: String,
    val uri: Uri,
    val parentTreeUri: Uri,
    var config: RomConfig,
    var lastPlayed: Date? = null,
)