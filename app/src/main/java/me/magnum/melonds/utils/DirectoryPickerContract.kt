package me.magnum.melonds.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.CallSuper
import com.llamalab.safs.FileSystems
import com.llamalab.safs.android.AndroidFileSystem

/**
 * A contract that launches the document picker to select a directory. Once selected, the URI
 * permission is persisted and returned to the callback. If no directory is selected, null is
 * returned.
 * Optionally, an URI for initial path may be provided
 */
class DirectoryPickerContract : ActivityResultContract<Uri?, Uri?>() {
    @CallSuper
    override fun createIntent(context: Context, input: Uri?): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

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
            (FileSystems.getDefault() as AndroidFileSystem).takePersistableUriPermission(intent)
            intent.data
        }
    }
}