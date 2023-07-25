package me.magnum.melonds.impl.emulator

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

class LifecycleOwnerProvider {

    private var currentLifecycleOwner: LifecycleOwner? = null

    fun getCurrentLifecycleOwner(): LifecycleOwner? {
        return currentLifecycleOwner
    }

    fun setCurrentLifecycleOwner(lifecycleOwner: LifecycleOwner) {
        currentLifecycleOwner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    currentLifecycleOwner = null
                }
            }
        })
    }
}