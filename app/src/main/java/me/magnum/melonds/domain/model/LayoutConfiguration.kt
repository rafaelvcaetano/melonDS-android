package me.magnum.melonds.domain.model

import java.util.UUID

data class LayoutConfiguration(
    val id: UUID?,
    val name: String?,
    val type: LayoutType,
    val orientation: LayoutOrientation,
    val useCustomOpacity: Boolean,
    val opacity: Int,
    val portraitLayout: UILayout,
    val landscapeLayout: UILayout
) {

    companion object {
        val DEFAULT_ID = UUID(0, 0)

        fun newCustom(portraitLayout: UILayout, landscapeLayout: UILayout): LayoutConfiguration {
            return LayoutConfiguration(null, null, LayoutType.CUSTOM, LayoutOrientation.FOLLOW_SYSTEM, false, 50, portraitLayout, landscapeLayout)
        }
    }

    // Empty constructor to include defaults that help in migrations
    constructor() : this(null, null, LayoutType.CUSTOM, LayoutOrientation.FOLLOW_SYSTEM, false, 50, UILayout(), UILayout())

    enum class LayoutType {
        DEFAULT,
        CUSTOM
    }

    enum class LayoutOrientation {
        FOLLOW_SYSTEM,
        PORTRAIT,
        LANDSCAPE
    }
}