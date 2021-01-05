package me.magnum.melonds.domain.model

enum class RuntimeConsoleType(val targetConsoleType: ConsoleType?) {
    DEFAULT(null),
    DS(ConsoleType.DS),
    DSi(ConsoleType.DSi)
}