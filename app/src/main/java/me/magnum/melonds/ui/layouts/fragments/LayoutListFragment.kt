package me.magnum.melonds.ui.layouts.fragments

import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.ui.layouts.BaseLayoutsFragment
import me.magnum.melonds.ui.layouts.BaseLayoutsViewModel
import me.magnum.melonds.ui.layouts.LayoutsViewModel

@AndroidEntryPoint
class LayoutListFragment : BaseLayoutsFragment() {
    override fun getFragmentViewModel(): BaseLayoutsViewModel {
        return activityViewModels<LayoutsViewModel>().value
    }
}