package me.magnum.melonds.domain.model

data class Cheat(
    val id: Long?,
    val cheatDatabaseId: Long,
    val name: String,
    val description: String?,
    val code: String,
    val enabled: Boolean,
) {

    fun isValid(): Boolean {
        // A cheat code can only have 128 parts (512 bytes). Since each part has 8 characters, we can add 1 to the length (to ensure that each part has a matching space
        // separator) and divide by 9 (part length + space separator) to calculate the total number of parts in the cheat
        return ((code.length + 1) / 9) <= 128
    }
}