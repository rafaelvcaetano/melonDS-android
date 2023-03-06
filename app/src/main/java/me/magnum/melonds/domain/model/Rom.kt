package me.magnum.melonds.domain.model

import android.net.Uri
import java.util.*

data class Rom(
    val name: String,
    val developerName: String,
    val fileName: String,
    val uri: Uri,
    val parentTreeUri: Uri,
    var config: RomConfig,
    var lastPlayed: Date? = null,
    val isDsiWareTitle: Boolean,
    val retroAchievementsHash: String,
) {

    fun hasSameFileAsRom(other: Rom): Boolean {
        return uri == other.uri
    }
}