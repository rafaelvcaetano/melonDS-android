package me.magnum.melonds.migrations.legacy

import android.net.Uri
import java.util.*

/**
 * ROM model used until app version 21.
 */
data class Rom21(
    val name: String,
    val uri: Uri,
    val parentTreeUri: Uri,
    var config: RomConfig1,
    var lastPlayed: Date? = null,
)