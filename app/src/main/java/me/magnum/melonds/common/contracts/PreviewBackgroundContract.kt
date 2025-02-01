package me.magnum.melonds.common.contracts

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.parcelables.BackgroundParcelable
import me.magnum.melonds.ui.backgroundpreview.BackgroundPreviewActivity

class PreviewBackgroundContract : ActivityResultContract<Background, Unit>() {

    override fun createIntent(context: Context, input: Background): Intent {
        return Intent(context, BackgroundPreviewActivity::class.java).apply {
            putExtra(BackgroundPreviewActivity.KEY_BACKGROUND, BackgroundParcelable(input))
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?) {
        /* no-op */
    }
}