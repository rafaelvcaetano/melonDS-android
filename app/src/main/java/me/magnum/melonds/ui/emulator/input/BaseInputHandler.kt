package me.magnum.melonds.ui.emulator.input

import android.view.View.OnTouchListener

abstract class BaseInputHandler(protected var inputListener: IInputListener) : OnTouchListener