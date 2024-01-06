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
import me.magnum.melonds.common.Permission
import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.ui.settings.FileStatusPopup

class BiosDirectoryPickerPreference(context: Context, attrs: AttributeSet?) : StoragePickerPreference(context, attrs) {
    interface BiosDirectoryValidator {
        fun getBiosDirectoryValidationResult(consoleType: ConsoleType, directory: Uri?): ConfigurationDirResult
    }

    init {
        widgetLayoutResource = R.layout.preference_directory_picker_status
        selectionType = SelectionType.DIRECTORY
        permissions = Permission.READ_WRITE
        persistPermissions = true
    }

    private var consoleType: ConsoleType? = null
    private var biosDirectoryValidator: BiosDirectoryValidator? = null
    private var currentValidationResult: ConfigurationDirResult? = null

    private var imageViewStatus: ImageView? = null

    override fun onDirectoryPicked(uri: Uri?) {
        super.onDirectoryPicked(uri)

        if (uri == null)
            return

        validateDirectory(uri)
    }

    override fun onDependencyChanged(dependency: Preference, disableDependent: Boolean) {
        super.onDependencyChanged(dependency, disableDependent)
        imageViewStatus?.isGone = disableDependent
    }

    fun setBiosDirectoryValidator(validator: BiosDirectoryValidator) {
        biosDirectoryValidator = validator
    }

    private fun validateDirectory(uri: Uri?) {
        if (!isEnabled) {
            imageViewStatus?.isGone = true
            return
        }

        imageViewStatus?.isGone = false

        currentValidationResult = biosDirectoryValidator?.getBiosDirectoryValidationResult(consoleType!!, uri)
        updateStatusView()
    }

    override fun initAttributes(attrs: AttributeSet?) {
        if (attrs == null)
            return

        val attrArray = context.theme.obtainStyledAttributes(attrs, R.styleable.BiosDirectoryPickerPreference, 0, 0)
        consoleType = ConsoleType.entries[attrArray.getIntOrThrow(R.styleable.BiosDirectoryPickerPreference_consoleType)]

        attrArray.recycle()
    }

    override fun onAttached() {
        super.onAttached()
        // Perform initial validation
        validateDirectory(getPersistedStringSet(emptySet()).firstOrNull()?.toUri())
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        imageViewStatus = holder.findViewById(R.id.imageViewStatus) as ImageView
        updateStatusView()
    }

    private fun updateStatusView() {
        val statusView = imageViewStatus ?: return

        statusView.isGone = !isEnabled

        if (!isEnabled) {
            return
        }

        currentValidationResult?.let { dirResult ->
            when (dirResult.status) {
                ConfigurationDirResult.Status.VALID -> {
                    (statusView.parent as View).visibility = View.GONE
                }
                ConfigurationDirResult.Status.INVALID -> {
                    (statusView.parent as View).visibility = View.VISIBLE
                    statusView.setImageResource(R.drawable.ic_status_warn)
                    ImageViewCompat.setImageTintList(statusView, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.statusWarn)))
                }
                ConfigurationDirResult.Status.UNSET -> {
                    (statusView.parent as View).visibility = View.VISIBLE
                    statusView.setImageResource(R.drawable.ic_status_error)
                    ImageViewCompat.setImageTintList(statusView, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.statusError)))
                }
            }
            statusView.setOnClickListener {
                FileStatusPopup(context, dirResult.fileResults).showAt(statusView)
            }
        }
    }
}