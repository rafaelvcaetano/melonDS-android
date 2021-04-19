package me.magnum.melonds.domain.model

import java.util.*

data class LayoutConfiguration(
        val id: UUID?,
        val name: String?,
        val type: LayoutType,
        val useCustomOpacity: Boolean,
        val opacity: Int,
        val portraitLayout: UILayout,
        val landscapeLayout: UILayout
) {

    companion object {
        val DEFAULT_ID = UUID(0, 0)

        fun newCustom(portraitLayout: UILayout, landscapeLayout: UILayout): LayoutConfiguration {
            return LayoutConfiguration(null, null, LayoutType.CUSTOM, false, 50, portraitLayout, landscapeLayout)
        }
    }

    enum class LayoutType {
        DEFAULT,
        CUSTOM
    }
}