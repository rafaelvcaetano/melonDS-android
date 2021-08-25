package me.magnum.melonds.extensions

fun String.isBlank(): Boolean {
    // Replace null terminators with another whitespace character
    val replacedString = this.replace('\u0000', '\u0009')
    return (replacedString as CharSequence).isBlank()
}