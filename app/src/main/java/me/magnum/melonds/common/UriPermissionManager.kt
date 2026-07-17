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

    // Same as [persistFilePermissions] but does not throw if [fileUri] was not granted a persistable permission
    fun tryPersistFilePermissions(fileUri: Uri, permission: Permission): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(fileUri, permission.toFlags())
            true
        } catch (e: SecurityException) {
            false
        }
    }
}