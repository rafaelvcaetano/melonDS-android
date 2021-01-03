package me.magnum.melonds.domain.model

enum class SortingMode(val defaultOrder: SortingOrder) {
    ALPHABETICALLY(SortingOrder.ASCENDING),
    RECENTLY_PLAYED(SortingOrder.DESCENDING)
}