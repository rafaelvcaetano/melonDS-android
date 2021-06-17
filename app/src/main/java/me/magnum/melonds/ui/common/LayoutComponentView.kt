package me.magnum.melonds.ui.common

import android.view.View
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.Rect

class LayoutComponentView(val view: View, val aspectRatio: Float, val component: LayoutComponent) {
    fun setPosition(position: Point) {
        view.updateLayoutParams<FrameLayout.LayoutParams> {
            leftMargin = position.x
            topMargin = position.y
        }
    }

    fun setSize(width: Int, height: Int) {
        view.updateLayoutParams {
            this.width = width
            this.height = height
        }
    }

    fun setPositionAndSize(position: Point, width: Int, height: Int) {
        view.updateLayoutParams<FrameLayout.LayoutParams> {
            this.width = width
            this.height = height
            leftMargin = position.x
            topMargin = position.y
        }
    }

    fun getPosition(): Point {
        return Point().apply {
            x = view.x.toInt()
            y = view.y.toInt()
        }
    }

    fun getWidth(): Int {
        return view.width
    }

    fun getHeight(): Int {
        return view.height
    }

    fun getRect(): Rect {
        return Rect(
                view.x.toInt(),
                view.y.toInt(),
                view.width,
                view.height
        )
    }
}