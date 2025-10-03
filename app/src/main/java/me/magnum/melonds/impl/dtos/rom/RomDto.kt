package me.magnum.melonds.impl.dtos.rom

import androidx.core.net.toUri
import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.rom.Rom
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

data class RomDto(
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
    var config: RomConfigDto,
    @SerializedName("lastPlayed")
    var lastPlayed: Date? = null,
    @SerializedName("isDsiWareTitle")
    val isDsiWareTitle: Boolean,
    @SerializedName("retroAchievementsHash")
    val retroAchievementsHash: String,
    @SerializedName("totalPlayTime")
    var totalPlayTime: Long = 0,
) {

    companion object {
        fun fromModel(rom: Rom): RomDto {
            return RomDto(
                rom.name,
                rom.developerName,
                rom.fileName,
                rom.uri.toString(),
                rom.parentTreeUri.toString(),
                RomConfigDto.fromModel(rom.config),
                rom.lastPlayed,
                rom.isDsiWareTitle,
                rom.retroAchievementsHash,
                rom.totalPlayTime.inWholeMilliseconds,
            )
        }
    }

    fun toModel(): Rom {
        return Rom(
            name,
            developerName,
            fileName,
            uri.toUri(),
            parentTreeUri.toUri(),
            config.toModel(),
            lastPlayed,
            isDsiWareTitle,
            retroAchievementsHash,
            totalPlayTime.milliseconds,
        )
    }
}
