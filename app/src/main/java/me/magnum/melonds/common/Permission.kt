package me.magnum.melonds.common

import android.content.Intent

enum class Permission {
    READ,
    WRITE,
    READ_WRITE;

    fun toFlags(): Int {
        return when (this) {
            READ -> Intent.FLAG_GRANT_READ_URI_PERMISSION
            WRITE -> Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            READ_WRITE -> Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
    }
}