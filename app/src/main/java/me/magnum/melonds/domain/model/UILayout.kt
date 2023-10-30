package me.magnum.melonds.domain.model

import java.util.UUID

data class UILayout(
    val backgroundId: UUID?,
    val backgroundMode: BackgroundMode,
    val components: List<PositionedLayoutComponent>,
) {
    // Empty constructor allow parsing after new data is added to the class
    constructor() : this(emptyList())

    constructor(components: List<PositionedLayoutComponent>): this(null, BackgroundMode.FIT_CENTER, components)
}