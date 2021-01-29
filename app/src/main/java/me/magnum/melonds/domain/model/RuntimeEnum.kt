package me.magnum.melonds.domain.model

interface RuntimeEnum<T, U> {
    fun getDefault(): T
    fun getValue(): U
}