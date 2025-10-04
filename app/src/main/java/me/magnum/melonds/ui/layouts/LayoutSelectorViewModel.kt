package me.magnum.melonds.ui.layouts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.repositories.LayoutsRepository
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LayoutSelectorViewModel @Inject constructor(layoutsRepository: LayoutsRepository, savedStateHandle: SavedStateHandle) : BaseLayoutsViewModel(layoutsRepository) {
    private var currentSelectedLayout: UUID?

    init {
        currentSelectedLayout = savedStateHandle.get<String?>(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID)?.let { UUID.fromString(it) }

        viewModelScope.launch {
            layoutsRepository.getLayouts().collect { layouts ->
                val layoutList = layouts.filter { it.target == LayoutConfiguration.LayoutTarget.INTERNAL }.toMutableList()
                layoutList.add(0, layoutsRepository.getGlobalLayoutPlaceholder())
                _layouts.value = layoutList
            }
        }
    }

    override fun getSelectedLayoutId(): UUID? {
        return currentSelectedLayout
    }

    override fun setSelectedLayoutId(id: UUID?) {
        currentSelectedLayout = id
    }
}