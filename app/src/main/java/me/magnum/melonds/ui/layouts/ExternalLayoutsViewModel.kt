package me.magnum.melonds.ui.layouts

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for managing external layouts.
 *
 * This ViewModel extends [BaseLayoutsViewModel] and is responsible for:
 * - Fetching and providing a list of available external layouts.
 * - Getting and setting the currently selected external layout ID.
 *
 * It uses [LayoutsRepository] to access layout data and [SettingsRepository]
 * to persist the selected external layout.
 *
 * @param layoutsRepository The repository for accessing layout data.
 * @param settingsRepository The repository for managing application settings, including the selected external layout.
 */
@HiltViewModel
class ExternalLayoutsViewModel @Inject constructor(
    layoutsRepository: LayoutsRepository,
    private val settingsRepository: SettingsRepository
) : BaseLayoutsViewModel(layoutsRepository) {
    init {
        viewModelScope.launch {
            layoutsRepository.getLayouts().collect {
                _layouts.value = it
            }
        }
    }

    override fun getSelectedLayoutId(): UUID {
        return settingsRepository.getExternalLayoutId()
    }

    override fun setSelectedLayoutId(id: UUID?) {
        id?.let { settingsRepository.setExternalLayoutId(it) }
    }
}