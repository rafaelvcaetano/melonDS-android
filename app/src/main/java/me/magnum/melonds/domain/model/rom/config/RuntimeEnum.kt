package me.magnum.melonds.domain.model.rom.config

interface RuntimeEnum<T, U> {
    fun getDefault(): T
    fun getValue(): U
}