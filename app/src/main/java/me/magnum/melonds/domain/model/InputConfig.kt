package me.magnum.melonds.domain.model

data class InputConfig(val input: Input, var key: Int = KEY_NOT_SET) {
    companion object {
        const val KEY_NOT_SET = -1
    }

    fun hasKeyAssigned(): Boolean {
        return key != KEY_NOT_SET
    }
}