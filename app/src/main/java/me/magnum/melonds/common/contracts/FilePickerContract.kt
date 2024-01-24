package me.magnum.melonds.common.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import me.magnum.melonds.common.Permission

/**
 * {@link ActivityResultContract} that launches the document picker and returns the Uri of the document selected by the user (if any). For input, a {@link Pair} can be
 * provided with the first parameter being the optional Uri of the initial location and the second parameter being the mime-types of the selectable documents. Multiple
 * mime-types can be specified in a single string be separating them with the pipe (|) character. If no mime-type is specified, it is assumed that all document types can be
 * selected.
 */
class FilePickerContract(private val permission: Permission) : ActivityResultContract<Pair<Uri?, Array<String>?>, Uri?>() {

    override fun createIntent(context: Context, input: Pair<Uri?, Array<String>?>): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .putExtra(Intent.EXTRA_MIME_TYPES, input.second ?: arrayOf("*/*"))
                .setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(permission.toFlags())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && input.first != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input.first)
        }

        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent == null || resultCode != Activity.RESULT_OK) {
            null
        } else {
            intent.data
        }
    }
}