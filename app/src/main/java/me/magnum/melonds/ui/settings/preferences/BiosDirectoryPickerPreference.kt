package me.magnum.melonds.ui.settings.preferences

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.res.getIntOrThrow
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.widget.ImageViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.ui.settings.FileStatusPopup

class BiosDirectoryPickerPreference(context: Context?, attrs: AttributeSet?) : StoragePickerPreference(context, attrs) {
    interface BiosDirectoryValidator {
        fun getBiosDirectoryValidationResult(consoleType: ConsoleType, directory: Uri?): ConfigurationDirResult
    }

    init {
        widgetLayoutResource = R.layout.preference_directory_picker_status
        selectionType = SelectionType.DIRECTORY
    }

    private var consoleType: ConsoleType? = null
    private var imageViewStatus: ImageView? = null
    private var biosDirectoryValidator: BiosDirectoryValidator? = null

    override fun onDirectoryPicked(uri: Uri?) {
        super.onDirectoryPicked(uri)

        if (uri == null)
            return

        updateStatusIndicator(uri)
    }

    override fun onDependencyChanged(dependency: Preference?, disableDependent: Boolean) {
        super.onDependencyChanged(dependency, disableDependent)
        imageViewStatus?.isGone = disableDependent
    }

    fun setBiosDirectoryValidator(validator: BiosDirectoryValidator) {
        biosDirectoryValidator = validator
    }

    private fun updateStatusIndicator(uri: Uri?) {
        if (!isEnabled) {
            imageViewStatus?.isGone = true
            return
        }

        imageViewStatus?.isGone = false

        biosDirectoryValidator?.getBiosDirectoryValidationResult(consoleType!!, uri)?.let { dirResult ->
            when (dirResult.status) {
                ConfigurationDirResult.Status.VALID -> {
                    (imageViewStatus!!.parent as View).visibility = View.GONE
                }
                ConfigurationDirResult.Status.INVALID -> {
                    (imageViewStatus!!.parent as View).visibility = View.VISIBLE
                    imageViewStatus!!.setImageResource(R.drawable.ic_status_warn)
                    ImageViewCompat.setImageTintList(imageViewStatus!!, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.statusWarn)))
                }
                ConfigurationDirResult.Status.UNSET ->{
                    (imageViewStatus!!.parent as View).visibility = View.VISIBLE
                    imageViewStatus!!.setImageResource(R.drawable.ic_status_error)
                    ImageViewCompat.setImageTintList(imageViewStatus!!, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.statusError)))
                }
            }
            imageViewStatus!!.setOnClickListener {
                FileStatusPopup(context, dirResult.fileResults).showAt(imageViewStatus!!)
            }
        }
    }

    override fun initAttributes(attrs: AttributeSet?) {
        if (attrs == null)
            return

        val attrArray = context.theme.obtainStyledAttributes(attrs, R.styleable.BiosDirectoryPickerPreference, 0, 0)
        consoleType = ConsoleType.values()[attrArray.getIntOrThrow(R.styleable.BiosDirectoryPickerPreference_consoleType)]

        attrArray.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        if (holder != null) {
            imageViewStatus = holder.findViewById(R.id.imageViewStatus) as ImageView
            updateStatusIndicator(getPersistedStringSet(emptySet()).firstOrNull()?.toUri())
        }
    }
}