package me.magnum.melonds.ui.backgrounds

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.domain.repositories.BackgroundRepository
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BackgroundsViewModel @Inject constructor(
    private val backgroundsRepository: BackgroundRepository,
    private val uriPermissionManager: UriPermissionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _backgrounds = MutableStateFlow<List<Background?>?>(null)
    val backgrounds = _backgrounds.asStateFlow()

    private val _currentSelectedBackground = MutableStateFlow<UUID?>(null)
    val currentSelectedBackground = _currentSelectedBackground.asStateFlow()

    init {
        val initialBackgroundId = savedStateHandle.get<String?>(BackgroundsActivity.KEY_INITIAL_BACKGROUND_ID)?.let { UUID.fromString(it) }
        _currentSelectedBackground.value = initialBackgroundId

        viewModelScope.launch {
            backgroundsRepository.getBackgrounds().collect {
                _backgrounds.value = buildList {
                    add(null)
                    addAll(it)
                }
            }
        }
    }

    fun selectBackground(background: Background?) {
        _currentSelectedBackground.value = background?.id
    }

    fun addBackground(background: Background) {
        uriPermissionManager.persistFilePermissions(background.uri, Permission.READ)
        viewModelScope.launch {
            backgroundsRepository.addBackground(background)
        }
    }

    fun deleteBackground(background: Background) {
        viewModelScope.launch {
            backgroundsRepository.deleteBackground(background)
            if (background.id == _currentSelectedBackground.value) {
                _currentSelectedBackground.value = null
            }
        }
    }
}