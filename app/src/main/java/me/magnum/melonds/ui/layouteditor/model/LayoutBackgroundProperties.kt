package me.magnum.melonds.ui.layouteditor.model

import me.magnum.melonds.domain.model.layout.BackgroundMode
import java.util.UUID

data class LayoutBackgroundProperties(
    val backgroundId: UUID?,
    val backgroundMode: BackgroundMode,
)