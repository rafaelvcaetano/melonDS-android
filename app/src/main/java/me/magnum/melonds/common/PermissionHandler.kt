package me.magnum.melonds.common

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription

class PermissionHandler(private val context: Context) {

    private val permissionRequestFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)
    private val permissionFlows = mutableMapOf<String, MutableSharedFlow<Unit>>()

    suspend fun checkPermission(permission: String): Boolean {
        return if (isPermissionGranted(permission)) {
            true
        } else {
            // Wait until we have the permission request result
            getOrCreatePermissionFlow(permission)
                .onSubscription {
                    // Trigger permission request
                    permissionRequestFlow.emit(permission)
                }
                .first()
            isPermissionGranted(permission)
        }
    }

    fun observePermissionRequests(): Flow<String> {
        return permissionRequestFlow
    }

    suspend fun notifyPermissionStatusUpdated(permission: String) {
        permissionFlows[permission]?.emit(Unit)
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOrCreatePermissionFlow(permission: String): MutableSharedFlow<Unit> {
        return permissionFlows.getOrPut(permission) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }
    }
}