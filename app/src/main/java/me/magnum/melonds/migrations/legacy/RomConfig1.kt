package me.magnum.melonds.migrations.legacy

import android.net.Uri
import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.rom.config.RuntimeConsoleType
import me.magnum.melonds.domain.model.rom.config.RuntimeMicSource
import java.util.*

data class RomConfig1(
    @SerializedName("a")
    val runtimeConsoleType: RuntimeConsoleType,
    @SerializedName("b")
    val runtimeMicSource: RuntimeMicSource,
    @SerializedName("c")
    val layoutId: UUID?,
    @SerializedName("d")
    val loadGbaCart: Boolean,
    @SerializedName("e")
    val gbaCartPath: Uri?,
    @SerializedName("f")
    val gbaSavePath: Uri?,
)
