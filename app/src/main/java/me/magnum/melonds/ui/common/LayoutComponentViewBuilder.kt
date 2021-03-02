package me.magnum.melonds.ui.common

import android.content.Context
import android.view.View

abstract class LayoutComponentViewBuilder {
    abstract fun build(context: Context): View
    abstract fun getAspectRatio(): Float
}