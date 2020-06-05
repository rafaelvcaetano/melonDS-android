package me.magnum.melonds.ui.input

import android.view.KeyEvent

interface INativeInputListener {
    fun onKeyEvent(keyEvent: KeyEvent): Boolean
}