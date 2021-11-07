package me.magnum.melonds.ui.emulator.rewind

import android.graphics.Rect
import android.view.View
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.recyclerview.widget.RecyclerView

/**
 * [RecyclerView.ItemDecoration] that adds spacing to the edge views so that these items always appear centered in the view. Only works for horizontal lists.
 */
class EdgeSpacingDecorator : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)

        val position = parent.getChildAdapterPosition(view)
        if (position == 0) {
            val offset = getEdgeViewOffset(view, parent)
            outRect.right = offset
        } else if (position == state.itemCount - 1) {
            val offset = getEdgeViewOffset(view, parent)
            outRect.left = offset
        }
    }

    private fun getEdgeViewOffset(view: View, parent: RecyclerView): Int {
        val viewWidth = if (view.width == 0) {
            // View size still unknown. Measure it
            view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            view.measuredWidth
        } else {
            view.width
        }

        return (parent.width - viewWidth - view.marginLeft - view.marginRight) / 2
    }
}