package me.magnum.melonds.ui.input

import me.magnum.melonds.model.Input
import me.magnum.melonds.model.Point

interface IInputListener {
    fun onKeyPress(key: Input)
    fun onKeyReleased(key: Input)
    fun onTouch(point: Point)
}