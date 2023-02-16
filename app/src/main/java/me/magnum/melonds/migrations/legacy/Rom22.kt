package me.magnum.melonds.migrations.legacy

import android.net.Uri
import java.util.*

/**
 * ROM model used from app version 22.
 */
data class Rom22(
    val name: String,
    val fileName: String,
    val uri: Uri,
    val parentTreeUri: Uri,
    var config: RomConfig1,
    var lastPlayed: Date? = null,
    val isDsiWareTitle: Boolean,
)
