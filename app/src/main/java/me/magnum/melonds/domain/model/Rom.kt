package me.magnum.melonds.domain.model

import android.net.Uri
import java.util.*

data class Rom(
    val name: String,
    val fileName: String,
    val uri: Uri,
    val parentTreeUri: Uri,
    var config: RomConfig,
    var lastPlayed: Date? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true

        if (other == null || javaClass != other.javaClass)
            return false

        val rom = other as Rom
        return uri == rom.uri
    }

    override fun hashCode(): Int {
        return uri.hashCode()
    }
}