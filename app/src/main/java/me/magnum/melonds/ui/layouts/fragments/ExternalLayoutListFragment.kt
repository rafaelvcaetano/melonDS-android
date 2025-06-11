package me.magnum.melonds.ui.layouts.fragments

import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.ui.layouts.BaseLayoutsFragment
import me.magnum.melonds.ui.layouts.BaseLayoutsViewModel
import me.magnum.melonds.ui.layouts.ExternalLayoutsViewModel
import java.util.UUID

@AndroidEntryPoint
class ExternalLayoutListFragment : BaseLayoutsFragment() {
    override fun getFragmentViewModel(): BaseLayoutsViewModel {
        return activityViewModels<ExternalLayoutsViewModel>().value
    }

    override fun getFallbackLayoutId(): UUID {
        return LayoutConfiguration.DEFAULT_ID
    }
}
