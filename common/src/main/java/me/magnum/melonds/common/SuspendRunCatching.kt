package me.magnum.melonds.common

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

suspend inline fun <R> suspendRunCatching(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: Throwable) {
        coroutineContext.ensureActive()
        Result.failure(e)
    }
}

suspend inline fun <R, T : R> Result<T>.suspendRecoverCatching(transform: (exception: Throwable) -> R): Result<R> {
    return when (val exception = exceptionOrNull()) {
        null -> this
        else -> suspendRunCatching { transform(exception) }
    }
}

suspend inline fun <R, T> Result<T>.suspendMapCatching(transform: (value: T) -> R): Result<R> {
    return when {
        isSuccess -> suspendRunCatching { transform(getOrThrow()) }
        else -> Result.failure(exceptionOrNull()!!)
    }
}