package me.magnum.melonds.ui.emulator.input

import android.view.KeyEvent

interface INativeInputListener {
    fun onKeyEvent(keyEvent: KeyEvent): Boolean
}