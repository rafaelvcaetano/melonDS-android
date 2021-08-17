package me.magnum.melonds.common

import android.content.Context
import android.net.Uri

class UriPermissionManager(private val context: Context) {

    fun persistDirectoryPermissions(directoryUri: Uri, permission: Permission) {
        val flags = permission.toFlags()
        context.contentResolver.takePersistableUriPermission(directoryUri, flags)
    }

    fun persistFilePermissions(fileUri: Uri, permission: Permission) {
        val flags = permission.toFlags()
        context.contentResolver.takePersistableUriPermission(fileUri, flags)
    }
}