package me.magnum.melonds.migrations.legacy

import android.net.Uri
import me.magnum.melonds.domain.model.RuntimeConsoleType
import me.magnum.melonds.domain.model.RuntimeMicSource
import java.util.*

data class RomConfig1(
    val runtimeConsoleType: RuntimeConsoleType,
    val runtimeMicSource: RuntimeMicSource,
    val layoutId: UUID?,
    val loadGbaCart: Boolean,
    val gbaCartPath: Uri?,
    val gbaSavePath: Uri?,
)
