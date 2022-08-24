package com.smp.masterswitchpreference

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import androidx.annotation.Keep
import androidx.preference.Preference
import java.io.File

@Keep
open class MasterSwitchPreference : Preference {
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        inflateAttrs(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        inflateAttrs(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        inflateAttrs(attrs)
    }

    constructor(context: Context, attrs: MasterSwitchPreferenceAttrs) : super(context) {
        extras.putParcelable(ATTRS_KEY_NAME, attrs)
    }

    override fun onAttached() {
        super.onAttached()
        setupStatus()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val myState = SavedState(superState);
        myState.attrs = attrs
        return myState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state == null || state::class.java != SavedState::class.java) {
            super.onRestoreInstanceState(state)
        } else {
            val myState = state as SavedState
            super.onRestoreInstanceState(myState.superState)
            extras.putParcelable(ATTRS_KEY_NAME, myState.attrs)
        }
    }

    private val attrs: MasterSwitchPreferenceAttrs
        get() = extras.getParcelable(ATTRS_KEY_NAME)!!


    internal class SavedState : BaseSavedState {
        var attrs: MasterSwitchPreferenceAttrs? = null

        constructor(source: Parcel) : super(source) {
            attrs = source.readParcelable(javaClass.classLoader)
        }

        constructor(superState: Parcelable?) : super(superState) {}

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeParcelable(attrs, 0)
        }

        companion object {
            val CREATOR: Parcelable.Creator<SavedState?> =
                object : Parcelable.Creator<SavedState?> {
                    override fun createFromParcel(`in`: Parcel): SavedState? {
                        return SavedState(`in`)
                    }

                    override fun newArray(size: Int): Array<SavedState?> {
                        return arrayOfNulls(size)
                    }
                }
        }
    }

    private fun inflateAttrs(attrs: AttributeSet?) {
        val resAttrs = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.MasterSwitchPreference,
            0,
            0
        ) ?: return

        with(resAttrs) {
            val libraryAttrs =
                context.theme.obtainStyledAttributes(attrs, R.styleable.Preference, 0, 0)

            val includedPrefScreenRes: String? =
                getString(R.styleable.MasterSwitchPreference_ms_includedPrefScreen)
            val notIncludedPrefScreenRes: String? =
                getString(R.styleable.MasterSwitchPreference_ms_excludedPrefScreen)
            val icon: String? = getString(R.styleable.MasterSwitchPreference_ms_explanationIcon)

            fun resInt(resName: String?, type: String): Int? {
                if (resName == null) return null
                val f = File(resName).nameWithoutExtension
                return context.resources.getIdentifier(f, type, context.packageName)
            }

            val def = MasterSwitchPreferenceAttrs()

            val attrs = MasterSwitchPreferenceAttrs(
                switchThumbColor = getColor(
                    R.styleable.MasterSwitchPreference_ms_switchThumbColor,
                    def.switchThumbColor
                ),
                switchTrackColor = getColor(
                    R.styleable.MasterSwitchPreference_ms_switchTrackColor,
                    def.switchTrackColor
                ),
                switchOnBackgroundColor = getColor(
                    R.styleable.MasterSwitchPreference_ms_switchOnBackgroundColor,
                    def.switchOnBackgroundColor
                ),
                switchOffBackgroundColor = getColor(
                    R.styleable.MasterSwitchPreference_ms_switchOffBackgroundColor,
                    def.switchOffBackgroundColor
                ),
                switchTextColor = getColor(
                    R.styleable.MasterSwitchPreference_ms_switchTextColor,
                    def.switchTextColor
                ),
                switchOffExplanationText = getString(R.styleable.MasterSwitchPreference_ms_switchOffExplanationText)
                    ?: def.switchOffExplanationText,
                switchOnExplanationText = getString(R.styleable.MasterSwitchPreference_ms_switchOnExplanationText)
                    ?: def.switchOnExplanationText,
                includedPrefScreen = resInt(includedPrefScreenRes, "xml")
                    ?: def.includedPrefScreen,
                excludedPrefScreen = resInt(notIncludedPrefScreenRes, "xml")
                    ?: def.excludedPrefScreen,
                switchOnText = getString(R.styleable.MasterSwitchPreference_ms_switchOnText)
                    ?: def.switchOnText,
                switchOffText = getString(R.styleable.MasterSwitchPreference_ms_switchOffText)
                    ?: def.switchOffText,
                key = this@MasterSwitchPreference.key ?: def.key,
                defaultValue = when {
                    libraryAttrs.hasValue(R.styleable.Preference_defaultValue) -> {
                        libraryAttrs.getBoolean(R.styleable.Preference_defaultValue, false)
                    }
                    libraryAttrs.hasValue(R.styleable.Preference_android_defaultValue) -> {
                        libraryAttrs.getBoolean(R.styleable.Preference_android_defaultValue, false)
                    }
                    else -> {
                        def.defaultValue
                    }
                },
                hideExplanation = getBoolean(
                    R.styleable.MasterSwitchPreference_ms_hideExplanation,
                    def.hideExplanation
                ),
                explanationIcon = resInt(icon, "drawable") ?: def.explanationIcon,
                showStatus = getBoolean(
                    R.styleable.MasterSwitchPreference_ms_showStatusInSummary,
                    def.showStatus
                )
            )

            val fragName = when {
                libraryAttrs.hasValue(R.styleable.Preference_fragment) -> {
                    libraryAttrs.getString(R.styleable.Preference_fragment)
                }
                libraryAttrs.hasValue(R.styleable.Preference_android_fragment) -> {
                    libraryAttrs.getString(R.styleable.Preference_android_fragment)
                }
                else -> {
                    MasterSwitchPreferenceFragment::class.qualifiedName
                }
            }
            setDefaultValue(attrs.defaultValue)
            key = attrs.key
            fragment = fragName
            extras.putParcelable(ATTRS_KEY_NAME, attrs)

            recycle()
            libraryAttrs.recycle()
        }
    }

    private fun setupStatus() {
        if (attrs.showStatus) {
            val on = getPersistedBoolean(attrs.defaultValue)
            summary = if (on) attrs.switchOnText else attrs.switchOffText
        }
    }
}