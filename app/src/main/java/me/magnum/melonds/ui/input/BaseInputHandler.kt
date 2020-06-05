package me.magnum.melonds.ui.input

import android.view.View.OnTouchListener

abstract class BaseInputHandler(protected var inputListener: IInputListener) : OnTouchListener