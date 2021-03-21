package me.magnum.melonds.common.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * A contract that launches the document picker to select a directory. Once selected, the URI
 * permission is persisted and returned to the callback. If no directory is selected, null is
 * returned.
 * Optionally, an URI for initial path may be provided
 */
class DirectoryPickerContract : ActivityResultContract<Uri?, Uri?>() {
    private lateinit var context: Context

    override fun createIntent(context: Context, input: Uri?): Intent {
        this.context = context
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && input != null)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)

        return intent
    }

    override fun getSynchronousResult(context: Context, input: Uri?): SynchronousResult<Uri?>? {
        return null
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent == null || resultCode != Activity.RESULT_OK)
            null
        else {
            intent.data?.let {
                val flags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
            }
            intent.data
        }
    }
}