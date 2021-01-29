package me.magnum.melonds.domain.model

enum class RuntimeConsoleType(val targetConsoleType: ConsoleType?) : RuntimeEnum<RuntimeConsoleType, ConsoleType> {
    DEFAULT(null),
    DS(ConsoleType.DS),
    DSi(ConsoleType.DSi);

    override fun getDefault() = DEFAULT
    override fun getValue() = targetConsoleType!!
}