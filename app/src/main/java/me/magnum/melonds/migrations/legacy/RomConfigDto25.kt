package me.magnum.melonds.migrations.legacy

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.RuntimeConsoleType
import me.magnum.melonds.domain.model.RuntimeMicSource
import java.util.*

data class RomConfigDto25(
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
)
