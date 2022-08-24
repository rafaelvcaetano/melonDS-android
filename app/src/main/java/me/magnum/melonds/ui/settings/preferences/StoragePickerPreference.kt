package me.magnum.melonds.ui.settings.preferences

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import androidx.preference.Preference
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission

open class StoragePickerPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    enum class SelectionType {
        FILE,
        DIRECTORY
    }

    var multiSelection: Boolean
        protected set

    var selectionType: SelectionType
        protected set

    var permissions: Permission
        protected set

    var persistPermissions: Boolean
        protected set

    var mimeType: String?
        protected set

    init {
        multiSelection = false
        selectionType = SelectionType.FILE
        permissions = Permission.READ
        persistPermissions = false
        mimeType = null
        initAttributes(attrs)
    }

    open fun onDirectoryPicked(uri: Uri?) {
        // TODO: properly add support for multi directory selection
        val dirs = if (uri == null) return else setOf(uri.toString())

        if (isPersistent) {
            persistStringSet(dirs)
        }

        onPreferenceChangeListener?.onPreferenceChange(this, dirs)
    }

    protected open fun initAttributes(attrs: AttributeSet?) {
        if (attrs == null)
            return

        val attrArray = context.theme.obtainStyledAttributes(attrs, R.styleable.DirectoryPickerPreference, 0, 0)
        val count = attrArray.indexCount
        for (i in 0..count) {
            val attr = attrArray.getIndex(i)
            when (attr) {
                R.styleable.DirectoryPickerPreference_selection -> multiSelection = attrArray.getInt(R.styleable.DirectoryPickerPreference_selection, 0) == 1
                R.styleable.DirectoryPickerPreference_type -> selectionType = SelectionType.values()[attrArray.getInt(R.styleable.DirectoryPickerPreference_type, 0)]
                R.styleable.DirectoryPickerPreference_permissions -> permissions = Permission.values()[attrArray.getInt(R.styleable.DirectoryPickerPreference_permissions, 0)]
                R.styleable.DirectoryPickerPreference_persistPermissions -> persistPermissions = attrArray.getBoolean(R.styleable.DirectoryPickerPreference_persistPermissions, false)
                R.styleable.DirectoryPickerPreference_mimeType -> mimeType = attrArray.getString(R.styleable.DirectoryPickerPreference_mimeType)
            }
        }
        attrArray.recycle()
    }
}