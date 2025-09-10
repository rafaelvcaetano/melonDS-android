package me.magnum.melonds.utils

import android.app.GameManager
import android.os.Build
import androidx.annotation.RequiresApi

object GameManagerCompat {

    enum class GameState {
        UNKNOWN,
        NONE,
        GAMEPLAY_INTERRUPTIBLE,
        GAMEPLAY_UNINTERRUPTIBLE,
        CONTENT
    }

    fun setGameState(gameManager: GameManager?, isLoading: Boolean, state: GameState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gameManager?.setGameState(android.app.GameState(isLoading, state.toServiceState()))
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun GameState.toServiceState(): Int {
        return when (this) {
            GameState.UNKNOWN -> android.app.GameState.MODE_UNKNOWN
            GameState.NONE -> android.app.GameState.MODE_NONE
            GameState.GAMEPLAY_INTERRUPTIBLE -> android.app.GameState.MODE_GAMEPLAY_INTERRUPTIBLE
            GameState.GAMEPLAY_UNINTERRUPTIBLE -> android.app.GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE
            GameState.CONTENT -> android.app.GameState.MODE_CONTENT
        }
    }
}