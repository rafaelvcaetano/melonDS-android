package me.magnum.melonds.utils

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Creates a [MutableSharedFlow] that holds a single value and has no initial value.
 */
@Suppress("FunctionName", "UNCHECKED_CAST")
fun <T> SubjectSharedFlow() = MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

/**
 * Creates a [MutableSharedFlow] that doesn't hold any value. Suitable to create flows used to fire events.
 */
@Suppress("FunctionName", "UNCHECKED_CAST")
fun <T> EventSharedFlow() = MutableSharedFlow<T>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)