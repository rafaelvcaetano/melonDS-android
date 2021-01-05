package me.magnum.melonds.domain.model

enum class ConsoleType(val consoleType: Int) {
    DS(0),
    DSi(1);

    companion object {
        fun valueOfIgnoreCase(value: String): ConsoleType {
            values().forEach {
                if (it.name.equals(value, true))
                    return it
            }
            throw IllegalArgumentException("Value $value does not represent an enum entry")
        }
    }
}