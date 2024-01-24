package me.magnum.melonds.ui.cheats.model

sealed class CheatsScreenUiState<T> {
    class Loading<T> : CheatsScreenUiState<T>()
    data class Ready<T>(val data: T) : CheatsScreenUiState<T>()
}