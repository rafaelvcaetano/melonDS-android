package me.magnum.melonds.ui.layouts.model

import java.util.UUID

data class SelectedLayout(
    val layoutId: UUID?,
    val reason: SelectionReason,
) {

    enum class SelectionReason {
        INITIAL_SELECTION,
        SELECTED_BY_USER,
        SELECTED_BY_FALLBACK,
    }
}