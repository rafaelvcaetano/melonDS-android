package me.magnum.melonds.domain.model

import java.util.*

data class UILayout(val backgroundId: UUID?, val backgroundMode: BackgroundMode, val components: List<PositionedLayoutComponent>) {
    // Empty constructor allow parsing after new data is added to the class
    constructor() : this(emptyList())

    constructor(components: List<PositionedLayoutComponent>): this(null, BackgroundMode.STRETCH, components)
}