package me.magnum.melonds.utils

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.lang.reflect.Array
import java.util.*
import kotlin.collections.ArrayList


object FileUtils {
    private const val TAG = "FileUtils"

    // TargetApi(21)
    private val isCompatible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    private const val DOWNLOADS_VOLUME_NAME = "downloads"
    private const val PRIMARY_VOLUME_NAME = "primary"
    private const val HOME_VOLUME_NAME = "home"

    @TargetApi(21)
    fun getAbsolutePathFromSAFUri(context: Context, safResultUri: Uri?): String? {
        val treeUri = DocumentsContract.buildDocumentUriUsingTree(safResultUri, DocumentsContract.getTreeDocumentId(safResultUri))
        return getAbsolutePathFromTreeUri(context, treeUri)
    }

    private fun getAbsolutePathFromTreeUri(context: Context, treeUri: Uri?): String? {
        if (!isCompatible) {
            Log.e(TAG, "getAbsolutePathFromTreeUri: called on unsupported API level")
            return null
        }
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
    @TargetApi(21)
    private fun getVolumePath(volumeId: String, context: Context): String? {
        if (!isCompatible) {
            Log.e(TAG, "getVolumePath called on unsupported API level")
            return null
        }
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
            val mStorageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
            val getVolumeList = mStorageManager.javaClass.getMethod("getVolumeList")
            val getUuid = storageVolumeClazz.getMethod("getUuid")
            val getPath = storageVolumeClazz.getMethod("getPath")
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
                    return getPath.invoke(storageVolumeElement) as String?
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getVolumePath exception", e)
        }
        Log.e(TAG, "getVolumePath failed for volumeId='$volumeId'")
        return null
    }

    /**
     * FileProvider does not support converting the absolute path from
     * getExternalFilesDir() to a "content://" Uri. As "file://" Uri
     * has been blocked since Android 7+, we need to build the Uri
     * manually after discovering the first external storage.
     * This is crucial to assist the user finding a writeable folder
     * to use syncthing's two way sync feature.
     */
    @TargetApi(19)
    fun getExternalFilesDirUri(context: Context): Uri? {
        try {
            /**
             * Determine the app's private data folder on external storage if present.
             * e.g. "/storage/abcd-efgh/Android/com.nutomic.syncthinandroid/files"
             */
            val externalFilesDir = ArrayList<File>()
            externalFilesDir.addAll(context.getExternalFilesDirs(null))
            externalFilesDir.remove(context.getExternalFilesDir(null))
            if (externalFilesDir.isEmpty()) {
                Log.w(TAG, "Could not determine app's private files directory on external storage.")
                return null
            }
            val absPath = externalFilesDir[0].absolutePath
            val segments = absPath.split("/").toTypedArray()
            if (segments.size < 2) {
                Log.w(TAG, "Could not extract volumeId from app's private files path '$absPath'")
                return null
            }
            // Extract the volumeId, e.g. "abcd-efgh"
            val volumeId = segments[2]
            // Build the content Uri for our private "files" folder.
            return Uri.parse(
                    "content://com.android.externalstorage.documents/document/" +
                            volumeId + "%3AAndroid%2Fdata%2F" +
                            context.packageName + "%2Ffiles")
        } catch (e: Exception) {
            Log.w(TAG, "getExternalFilesDirUri exception", e)
        }
        return null
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getVolumeIdFromTreeUri(treeUri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val split = docId.split(":").toTypedArray()
        return if (split.isNotEmpty()) {
            split[0]
        } else {
            null
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getDocumentPathFromTreeUri(treeUri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val split: kotlin.Array<String?> = docId.split(":").toTypedArray()
        return if (split.size >= 2 && split[1] != null) split[1] else File.separator
    }

    fun cutTrailingSlash(path: String): String? {
        return if (path.endsWith(File.separator)) {
            path.substring(0, path.length - 1)
        } else path
    }
}