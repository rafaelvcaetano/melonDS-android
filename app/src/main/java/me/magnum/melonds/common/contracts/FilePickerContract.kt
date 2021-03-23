package me.magnum.melonds.common.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

class FilePickerContract(private val persistUris: Boolean = true) : ActivityResultContract<Pair<Uri?, String?>, Uri?>() {
    private lateinit var context: Context

    override fun createIntent(context: Context, input: Pair<Uri?, String?>): Intent {
        this.context = context

        var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        if (persistUris) {
            flags = flags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType(input.second ?: "*/*")
                .addFlags(flags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && input.first != null)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input.first)

        return intent
    }

    override fun getSynchronousResult(context: Context, input: Pair<Uri?, String?>): SynchronousResult<Uri?>? {
        return null
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent == null || resultCode != Activity.RESULT_OK)
            null
        else {
            if (persistUris) {
                intent.data?.let {
                    val flags =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    this.context.contentResolver.takePersistableUriPermission(it, flags)
                }
            }
            intent.data
        }
    }
}