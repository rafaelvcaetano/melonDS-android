package me.magnum.melonds.model

enum class SortingMode(val defaultOrder: SortingOrder) {
    ALPHABETICALLY(SortingOrder.ASCENDING),
    RECENTLY_PLAYED(SortingOrder.DESCENDING)
}