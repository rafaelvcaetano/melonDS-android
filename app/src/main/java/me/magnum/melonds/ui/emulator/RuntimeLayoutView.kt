package me.magnum.melonds.ui.emulator

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isInvisible
import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.ui.common.LayoutView

class RuntimeLayoutView(context: Context, attrs: AttributeSet?) : LayoutView(context, attrs) {
    fun setSoftInputVisibility(visible: Boolean) {
        getLayoutComponentViews().forEach {
            if (it.component != LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT && it.component != LayoutComponent.BOTTOM_SCREEN) {
                it.view.isInvisible = !visible
            }
        }
    }
}