package me.magnum.melonds.migrations.legacy

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * ROM DTO used from app version 27.
 */
data class RomDto31(
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
    var config: RomConfigDto31,
    @SerializedName("lastPlayed")
    var lastPlayed: Date? = null,
    @SerializedName("isDsiWareTitle")
    val isDsiWareTitle: Boolean,
    @SerializedName("retroAchievementsHash")
    val retroAchievementsHash: String,
)