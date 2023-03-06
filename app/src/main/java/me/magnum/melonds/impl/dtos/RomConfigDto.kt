package me.magnum.melonds.impl.dtos

import android.net.Uri
import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RuntimeConsoleType
import me.magnum.melonds.domain.model.RuntimeMicSource
import java.util.*

data class RomConfigDto(
    @SerializedName("runtimeConsoleType")
    val runtimeConsoleType: RuntimeConsoleType,
    @SerializedName("runtimeMicSource")
    val runtimeMicSource: RuntimeMicSource,
    @SerializedName("layoutId")
    val layoutId: String?,
    @SerializedName("loadGbaCart")
    val loadGbaCart: Boolean,
    @SerializedName("gbaCartPath")
    val gbaCartPath: String?,
    @SerializedName("gbaSavePath")
    val gbaSavePath: String?,
) {

    companion object {
        fun fromModel(romConfig: RomConfig): RomConfigDto {
            return RomConfigDto(
                romConfig.runtimeConsoleType,
                romConfig.runtimeMicSource,
                romConfig.layoutId?.toString(),
                romConfig.loadGbaCart,
                romConfig.gbaCartPath?.toString(),
                romConfig.gbaSavePath?.toString(),
            )
        }
    }

    fun toModel(): RomConfig {
        return RomConfig(
            runtimeConsoleType,
            runtimeMicSource,
            layoutId?.let { UUID.fromString(it) },
            loadGbaCart,
            gbaCartPath?.let { Uri.parse(it) },
            gbaSavePath?.let { Uri.parse(it) },
        )
    }
}