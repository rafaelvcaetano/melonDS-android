package me.magnum.melonds.ui.settings.preferences

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.res.getIntOrThrow
import androidx.core.widget.ImageViewCompat
import androidx.preference.PreferenceViewHolder
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.ui.settings.FileStatusPopup
import me.magnum.melonds.utils.ConfigurationUtils

class BiosDirectoryPickerPreference(context: Context?, attrs: AttributeSet?) : StoragePickerPreference(context, attrs) {
    init {
        widgetLayoutResource = R.layout.preference_directory_picker_status
        selectionType = SelectionType.DIRECTORY
    }

    lateinit var consoleType: ConsoleType
    lateinit var imageViewStatus: ImageView

    override fun onDirectoryPicked(uri: Uri?) {
        super.onDirectoryPicked(uri)

        if (uri == null)
            return

        updateStatusIndicator(uri)
    }

    private fun updateStatusIndicator(uri: Uri?) {
        val dirResult = ConfigurationUtils.checkConfigurationDirectory(context, uri, consoleType)
        when (dirResult.status) {
            ConfigurationUtils.ConfigurationDirStatus.VALID -> {
                (imageViewStatus.parent as View).visibility = View.GONE
            }
            ConfigurationUtils.ConfigurationDirStatus.INVALID -> {
                (imageViewStatus.parent as View).visibility = View.VISIBLE
                imageViewStatus.setImageResource(R.drawable.ic_status_warn)
                ImageViewCompat.setImageTintList(imageViewStatus, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.statusWarn)))
            }
            ConfigurationUtils.ConfigurationDirStatus.UNSET ->{
                (imageViewStatus.parent as View).visibility = View.VISIBLE
                imageViewStatus.setImageResource(R.drawable.ic_status_error)
                ImageViewCompat.setImageTintList(imageViewStatus, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.statusError)))
            }
        }
        imageViewStatus.setOnClickListener {
            FileStatusPopup(context, dirResult.fileResults).showAt(imageViewStatus)
        }
    }

    override fun initAttributes(attrs: AttributeSet?) {
        if (attrs == null)
            return

        val attrArray = context.theme.obtainStyledAttributes(attrs, R.styleable.BiosDirectoryPickerPreference, 0, 0)
        val count = attrArray.indexCount
        for (i in 0..count) {
            val attr = attrArray.getIndex(i)
            when (attr) {
                R.styleable.BiosDirectoryPickerPreference_consoleType -> consoleType = ConsoleType.values()[attrArray.getIntOrThrow(attr)]
            }
        }
        attrArray.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        if (holder != null) {
            imageViewStatus = holder.findViewById(R.id.imageViewStatus) as ImageView
            updateStatusIndicator(getPersistedStringSet(emptySet()).firstOrNull()?.let { Uri.parse(it) })
        }
    }
}