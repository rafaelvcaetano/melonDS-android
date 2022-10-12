package me.magnum.melonds.common.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.core.net.toUri
import java.io.File

class UpdateContentProvider : ContentProvider() {
    companion object {
        fun getUpdateFileUri(context: Context, file: File): Uri {
            val fileUri = file.toUri()
            val builder = Uri.Builder()
                .authority("${context.packageName}.provider")
                .scheme("file")
                .path(fileUri.path)
                .query(fileUri.query)
                .fragment(fileUri.fragment)

            return builder.build()
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }
}