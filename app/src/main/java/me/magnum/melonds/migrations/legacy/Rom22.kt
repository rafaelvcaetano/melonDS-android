package me.magnum.melonds.migrations.legacy

import android.net.Uri
import com.google.gson.annotations.SerializedName
import java.util.*

/**
 * ROM model used from app version 22.
 */
data class Rom22(
    @SerializedName("a")
    val name: String,
    @SerializedName("b")
    val fileName: String,
    @SerializedName("c")
    val uri: Uri,
    @SerializedName("d")
    val parentTreeUri: Uri,
    @SerializedName("e")
    val config: RomConfig1,
    @SerializedName("f")
    val lastPlayed: Date? = null,
    @SerializedName("g")
    val isDsiWareTitle: Boolean,
)
