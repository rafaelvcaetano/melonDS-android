package me.magnum.melonds.ui.layouts.fragments

import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.ui.layouts.BaseLayoutsFragment
import me.magnum.melonds.ui.layouts.BaseLayoutsViewModel
import me.magnum.melonds.ui.layouts.ExternalLayoutSelectorViewModel
import java.util.UUID

@AndroidEntryPoint
class ExternalLayoutSelectorFragment : BaseLayoutsFragment() {
    override fun getFragmentViewModel(): BaseLayoutsViewModel {
        return activityViewModels<ExternalLayoutSelectorViewModel>().value
    }

    override fun getFallbackLayoutId(): UUID? {
        return null
    }
}
