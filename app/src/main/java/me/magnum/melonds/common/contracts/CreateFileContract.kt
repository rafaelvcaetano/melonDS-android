package me.magnum.melonds.common.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

class CreateFileContract : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .putExtra(Intent.EXTRA_TITLE, input)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent == null || resultCode != Activity.RESULT_OK) {
            null
        } else {
            intent.data
        }
    }
}