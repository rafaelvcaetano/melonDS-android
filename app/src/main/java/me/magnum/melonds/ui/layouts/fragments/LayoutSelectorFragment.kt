package me.magnum.melonds.ui.layouts.fragments

import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.ui.layouts.BaseLayoutsFragment
import me.magnum.melonds.ui.layouts.BaseLayoutsViewModel
import me.magnum.melonds.ui.layouts.LayoutSelectorViewModel
import java.util.*

@AndroidEntryPoint
class LayoutSelectorFragment : BaseLayoutsFragment() {
    override fun getFragmentViewModel(): BaseLayoutsViewModel {
        return activityViewModels<LayoutSelectorViewModel>().value
    }

    override fun getFallbackLayoutId(): UUID? {
        return null
    }
}