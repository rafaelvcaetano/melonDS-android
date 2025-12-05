package me.magnum.melonds.domain.model

fun DualScreenPreset.defaultInternalAlignment(): ScreenAlignment {
    return when (this) {
        DualScreenPreset.INTERNAL_TOP_EXTERNAL_BOTTOM -> ScreenAlignment.BOTTOM
        DualScreenPreset.INTERNAL_BOTTOM_EXTERNAL_TOP -> ScreenAlignment.TOP
        DualScreenPreset.OFF -> ScreenAlignment.TOP
    }
}

fun DualScreenPreset.defaultExternalAlignment(): ScreenAlignment {
    return when (this) {
        DualScreenPreset.INTERNAL_TOP_EXTERNAL_BOTTOM -> ScreenAlignment.TOP
        DualScreenPreset.INTERNAL_BOTTOM_EXTERNAL_TOP -> ScreenAlignment.BOTTOM
        DualScreenPreset.OFF -> ScreenAlignment.TOP
    }
}
