package me.magnum.melonds.ui.common

import android.view.View
import android.widget.FrameLayout
import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.Rect

class LayoutComponentView(val view: View, val aspectRatio: Float, val component: LayoutComponent) {
    fun setPosition(position: Point) {
        val newParams = FrameLayout.LayoutParams(view.width, view.height).apply {
            leftMargin = position.x
            topMargin = position.y
        }
        view.layoutParams = newParams
    }

    fun setSize(width: Int, height: Int) {
        val newParams = FrameLayout.LayoutParams(view.width, view.height)
        view.layoutParams = newParams
    }

    fun setPositionAndSize(position: Point, width: Int, height: Int) {
        val newParams = FrameLayout.LayoutParams(width, height).apply {
            leftMargin = position.x
            topMargin = position.y
        }
        view.layoutParams = newParams
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