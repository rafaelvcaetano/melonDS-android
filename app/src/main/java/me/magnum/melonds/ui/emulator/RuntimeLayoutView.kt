package me.magnum.melonds.ui.emulator

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import androidx.core.view.isInvisible
import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.ui.common.LayoutView

class RuntimeLayoutView(context: Context, attrs: AttributeSet?) : LayoutView(context, attrs) {
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        currentLayoutConfiguration?.let {
            instantiateLayout(it)
        }
    }

    fun setSoftInputVisibility(visible: Boolean) {
        getLayoutComponentViews().forEach {
            if (it.component != LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT && !it.component.isScreen()) {
                it.view.isInvisible = !visible
            }
        }
    }
}