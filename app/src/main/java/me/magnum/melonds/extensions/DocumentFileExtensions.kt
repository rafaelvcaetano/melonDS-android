package me.magnum.melonds.extensions

import androidx.documentfile.provider.DocumentFile

val DocumentFile.nameWithoutExtension get() = name?.substringBeforeLast('.')