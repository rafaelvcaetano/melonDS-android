package me.magnum.melonds.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.system.Os
import android.util.Log
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.lang.reflect.Array

object FileUtils {
    private const val TAG = "FileUtils"

    private const val DOWNLOADS_VOLUME_NAME = "downloads"
    private const val PRIMARY_VOLUME_NAME = "primary"
    private const val HOME_VOLUME_NAME = "home"

    fun getAbsolutePathFromSAFUri(context: Context, safResultUri: Uri?): String? {
        if (safResultUri == null) {
            return null
        }

        val uriSchema = getUriSchema(safResultUri)

        return when (uriSchema) {
            "content" -> getFilePathFromSafUri(context, safResultUri)
            "file" -> getFilePathFromFileUri(safResultUri)
            else -> null
        }
    }

    private fun getUriSchema(uri: Uri): String? {
        val uriString = uri.toString()
        val indexOfSeparator = uriString.indexOf("://")
        if (indexOfSeparator == -1)
            return null

        return uriString.substring(0 until indexOfSeparator)
    }

    private fun getFilePathFromFileUri(fileUri: Uri): String {
        val uriString = fileUri.toString()
        val indexOfSeparator = uriString.indexOf("://")
        return uriString.substring(indexOfSeparator + 3)
    }

    private fun getFilePathFromSafUri(context: Context, safUri: Uri): String? {
        val isSingleDocument = DocumentsContract.isDocumentUri(context, safUri)

        return try {
            if (isSingleDocument) {
                getAbsolutePathFromSingleUri(context, safUri)
            } else {
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(safUri, DocumentsContract.getTreeDocumentId(safUri))
                getAbsolutePathFromTreeUri(context, documentUri)
            }
        } catch (e: Exception) {
            // Fallback to simple filename if the path cannot be determined
            if (isSingleDocument) {
                DocumentFile.fromSingleUri(context, safUri)?.name
            } else {
                DocumentFile.fromTreeUri(context, safUri)?.name
            }
        }
    }

    private fun getAbsolutePathFromSingleUri(context: Context, uri: Uri): String? {
        return context.contentResolver.openFileDescriptor(uri, "r")?.use {
            val file = File("/proc/self/fd/${it.fd}")
            Os.readlink(file.absolutePath)
        }
    }

    private fun getAbsolutePathFromTreeUri(context: Context, treeUri: Uri?): String? {
        if (treeUri == null) {
            Log.w(TAG, "getAbsolutePathFromTreeUri: called with treeUri == null")
            return null
        }

        // Determine volumeId, e.g. "home", "documents"
        val volumeId = getVolumeIdFromTreeUri(treeUri) ?: return null

        // Handle Uri referring to internal or external storage.
        var volumePath = getVolumePath(volumeId, context) ?: return File.separator
        if (volumePath.endsWith(File.separator)) {
            volumePath = volumePath.substring(0, volumePath.length - 1)
        }
        var documentPath = getDocumentPathFromTreeUri(treeUri)
        if (documentPath!!.endsWith(File.separator)) {
            documentPath = documentPath.substring(0, documentPath.length - 1)
        }
        return if (documentPath.isNotEmpty()) {
            if (documentPath.startsWith(File.separator)) {
                volumePath + documentPath
            } else {
                volumePath + File.separator + documentPath
            }
        } else {
            volumePath
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun getVolumePath(volumeId: String, context: Context): String? {
        try {
            if (HOME_VOLUME_NAME == volumeId) {
                Log.v(TAG, "getVolumePath: isHomeVolume")
                // Reading the environment var avoids hard coding the case of the "documents" folder.
                return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
            }
            if (DOWNLOADS_VOLUME_NAME == volumeId) {
                Log.v(TAG, "getVolumePath: isDownloadsVolume")
                // Reading the environment var avoids hard coding the case of the "downloads" folder.
                return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            }
            val mStorageManager = context.getSystemService<StorageManager>()!!
            val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
            val getVolumeList = mStorageManager.javaClass.getMethod("getVolumeList")
            val getUuid = storageVolumeClazz.getMethod("getUuid")
            val getPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                storageVolumeClazz.getMethod("getDirectory")
            } else {
                storageVolumeClazz.getMethod("getPath")
            }
            val isPrimary = storageVolumeClazz.getMethod("isPrimary")
            val result: Any = getVolumeList.invoke(mStorageManager)
            val length = Array.getLength(result)
            for (i in 0 until length) {
                val storageVolumeElement = Array.get(result, i)
                val uuid = getUuid.invoke(storageVolumeElement) as String?
                val primary = isPrimary.invoke(storageVolumeElement) as Boolean
                val isPrimaryVolume = primary && PRIMARY_VOLUME_NAME == volumeId
                val isExternalVolume = uuid != null && uuid == volumeId
                Log.d(TAG, "Found volume with uuid='" + uuid +
                        "', volumeId='" + volumeId +
                        "', primary=" + primary +
                        ", isPrimaryVolume=" + isPrimaryVolume +
                        ", isExternalVolume=" + isExternalVolume
                )
                if (isPrimaryVolume || isExternalVolume) {
                    Log.v(TAG, "getVolumePath: isPrimaryVolume || isExternalVolume")
                    // Return path if the correct volume corresponding to volumeId was found.
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        (getPath.invoke(storageVolumeElement) as File?)?.absolutePath
                    } else {
                        return getPath.invoke(storageVolumeElement) as String?
                    }
                }
            }

            // Fallback to simple storage path
            return "/storage/${volumeId}"
        } catch (e: Exception) {
            Log.w(TAG, "getVolumePath exception", e)
        }
        Log.e(TAG, "getVolumePath failed for volumeId='$volumeId'")
        return null
    }

    private fun getVolumeIdFromTreeUri(treeUri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val split = docId.split(":").toTypedArray()
        return if (split.isNotEmpty()) {
            split[0]
        } else {
            null
        }
    }

    private fun getDocumentPathFromTreeUri(treeUri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val split = docId.split(":").toTypedArray()
        return if (split.size >= 2) split[1] else File.separator
    }
}