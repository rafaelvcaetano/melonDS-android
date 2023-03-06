package me.magnum.melonds.migrations.legacy

import com.google.gson.annotations.SerializedName
import java.util.*

/**
 * ROM DTO used from app version 25.
 */
data class RomDto25(
    @SerializedName("name")
    val name: String,
    @SerializedName("developerName")
    val developerName: String,
    @SerializedName("fileName")
    val fileName: String,
    @SerializedName("uri")
    val uri: String,
    @SerializedName("parentTreeUri")
    val parentTreeUri: String,
    @SerializedName("config")
    var config: RomConfigDto25,
    @SerializedName("lastPlayed")
    var lastPlayed: Date? = null,
    @SerializedName("isDsiWareTitle")
    val isDsiWareTitle: Boolean,
    @SerializedName("retroAchievementsHash")
    val retroAchievementsHash: String,
)