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
 * A contract that launches the document picker to select a directory. Once selected, the URI
 * permission is returned to the callback. If no directory is selected, null is returned.
 * Optionally, an URI for the initial path may be provided.
 */
class DirectoryPickerContract(private val permissions: Permission) : ActivityResultContract<Uri?, Uri?>() {

    override fun createIntent(context: Context, input: Uri?): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            permissions.toFlags() or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && input != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
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