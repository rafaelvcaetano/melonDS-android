package me.magnum.melonds.ui.cheats.model

data class CheatSubmissionForm(
    val name: String,
    val description: String,
    val code: String,
) {

    fun isValid(): Boolean {
        return name.isNotBlank() && code.isNotBlank()
    }
}