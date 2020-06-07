package me.magnum.melonds.extensions

import android.view.View
import android.view.ViewGroup
import androidx.core.view.children

fun View.setViewEnabledRecursive(enabled: Boolean) {
    this.isEnabled = enabled
    if (this is ViewGroup) {
        this.children.forEach { it.setViewEnabledRecursive(enabled) }
    }
}