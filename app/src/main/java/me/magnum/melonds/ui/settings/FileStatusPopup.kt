package me.magnum.melonds.ui.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import me.magnum.melonds.R
import me.magnum.melonds.utils.ConfigurationUtils

class FileStatusPopup(context: Context, fileStatuses: Array<Pair<String, ConfigurationUtils.ConfigurationFileStatus>>) {
    private val popup: PopupWindow

    init {
        val layoutInflater = LayoutInflater.from(context)
        val view = layoutInflater.inflate(R.layout.dialog_config_files, null)
        val linearLayout = view.findViewById<LinearLayout>(R.id.layoutFileItems)
        popup = PopupWindow(view, RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT).apply {
            isOutsideTouchable = true
        }

        fileStatuses.forEach {
            val itemView = layoutInflater.inflate(R.layout.item_file_status, null)
            val imageViewStatus = itemView.findViewById<ImageView>(R.id.imageViewFileStatus)
            val textFileName = itemView.findViewById<TextView>(R.id.textFileName)

            when (it.second) {
                ConfigurationUtils.ConfigurationFileStatus.PRESENT -> {
                    imageViewStatus.setImageResource(R.drawable.ic_status_ok)
                    ImageViewCompat.setImageTintList(imageViewStatus, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.statusOk)))
                }
                ConfigurationUtils.ConfigurationFileStatus.MISSING -> {
                    imageViewStatus.setImageResource(R.drawable.ic_status_error)
                    ImageViewCompat.setImageTintList(imageViewStatus, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.statusError)))
                }
            }
            textFileName.text = it.first
            linearLayout.addView(itemView)
        }
    }

    fun showAt(view: View) {
        popup.contentView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val rect = locateView(view)
        popup.showAtLocation(view, Gravity.TOP or Gravity.START, rect.right - popup.contentView.measuredWidth, rect.top + rect.height())
    }

    private fun locateView(v: View): Rect {
        val point = IntArray(2)
        v.getLocationOnScreen(point)

        val location = Rect()
        location.left = point[0]
        location.top = point[1]
        location.right = point[0] + v.width
        location.bottom = point[1] + v.height
        return location
    }
}