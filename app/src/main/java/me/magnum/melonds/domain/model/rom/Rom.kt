package me.magnum.melonds.domain.model.rom

import android.net.Uri
import me.magnum.melonds.domain.model.rom.config.RomConfig
import java.util.*
import kotlin.time.Duration

data class Rom(
    val name: String,
    val developerName: String,
    val fileName: String,
    val uri: Uri,
    val parentTreeUri: Uri?,
    var config: RomConfig,
    var lastPlayed: Date? = null,
    val isDsiWareTitle: Boolean,
    val retroAchievementsHash: String,
    val totalPlayTime: Duration = Duration.ZERO,
    /** 0 only for identities loaded from the pre-digest internal ROM cache. */
    val storageNameVersion: Int = 1,
) {

    fun hasSameFileAsRom(other: Rom): Boolean {
        return uri == other.uri
    }
}
