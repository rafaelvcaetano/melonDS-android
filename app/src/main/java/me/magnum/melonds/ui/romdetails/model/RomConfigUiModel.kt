package me.magnum.melonds.ui.romdetails.model

import android.net.Uri
import me.magnum.melonds.domain.model.RuntimeConsoleType
import me.magnum.melonds.domain.model.RuntimeMicSource
import java.util.*

data class RomConfigUiModel(
    val runtimeConsoleType: RuntimeConsoleType = RuntimeConsoleType.DEFAULT,
    val runtimeMicSource: RuntimeMicSource = RuntimeMicSource.DEFAULT,
    val layoutId: UUID? = null,
    val layoutName: String? = null,
    val loadGbaCart: Boolean = false,
    val gbaCartPath: String? = null,
    val gbaCartUri: Uri? = null,
    val gbaSavePath: String? = null,
    val gbaSaveUri: Uri? = null,
)
