package me.magnum.melonds.ui.layouts

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LayoutsViewModel @Inject constructor(layoutsRepository: LayoutsRepository, private val settingsRepository: SettingsRepository) : BaseLayoutsViewModel(layoutsRepository) {
    init {
        viewModelScope.launch {
            layoutsRepository.getLayouts().collect {
                _layouts.value = it
            }
        }
    }

    override fun getSelectedLayoutId(): UUID {
        return settingsRepository.getSelectedLayoutId()
    }

    override fun setSelectedLayoutId(id: UUID?) {
        id?.let {
            settingsRepository.setSelectedLayoutId(it)
        }
    }
}