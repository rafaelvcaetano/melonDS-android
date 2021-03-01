package me.magnum.melonds.domain.model

import java.util.*

data class LayoutConfiguration(val id: UUID?, val name: String?, val portraitLayout: UILayout, val landscapeLayout: UILayout) {
    companion object {
        fun new(portraitLayout: UILayout, landscapeLayout: UILayout): LayoutConfiguration {
            return LayoutConfiguration(null, null, portraitLayout, landscapeLayout)
        }
    }
}