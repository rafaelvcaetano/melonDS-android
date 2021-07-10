package me.magnum.melonds.domain.model

import android.net.Uri

data class AppUpdate(
    val id: Long,
    val downloadUri: Uri,
    val newVersion: Version,
    val description: String,
    val binarySize: Long
)