package me.magnum.melonds.domain.model.layout

import java.util.UUID

data class LayoutConfiguration(
    val id: UUID?,
    val name: String?,
    val type: LayoutType,
    val orientation: LayoutOrientation,
    val useCustomOpacity: Boolean,
    val opacity: Int,
    val layoutVariants: Map<UILayoutVariant, UILayout>,
    val target: LayoutTarget = LayoutTarget.INTERNAL,
) {

    companion object {
        val DEFAULT_ID = UUID(0, 0)

        fun newCustom(target: LayoutTarget = LayoutTarget.INTERNAL): LayoutConfiguration {
            return LayoutConfiguration(
                id = null,
                name = null,
                type = LayoutType.CUSTOM,
                orientation = LayoutOrientation.FOLLOW_SYSTEM,
                useCustomOpacity = false,
                opacity = 50,
                layoutVariants = emptyMap(),
                target = target,
            )
        }
    }

    // Empty constructor to include defaults that help in migrations
    constructor() : this(null, null, LayoutType.CUSTOM, LayoutOrientation.FOLLOW_SYSTEM, false, 50, emptyMap(), LayoutTarget.INTERNAL)

    enum class LayoutType {
        DEFAULT,
        CUSTOM
    }

    enum class LayoutOrientation {
        FOLLOW_SYSTEM,
        PORTRAIT,
        LANDSCAPE
    }

    enum class LayoutTarget {
        INTERNAL,
        EXTERNAL,
    }
}