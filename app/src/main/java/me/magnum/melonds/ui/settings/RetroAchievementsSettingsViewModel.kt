package me.magnum.melonds.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.melonds.ui.settings.model.RetroAchievementsAccountState
import javax.inject.Inject

@HiltViewModel
class RetroAchievementsSettingsViewModel @Inject constructor(
    private val retroAchievementsRepository: RetroAchievementsRepository,
): ViewModel() {

    private val _accountState = MutableStateFlow<RetroAchievementsAccountState>(RetroAchievementsAccountState.Unknown)
    val accountState by lazy {
        viewModelScope.launch {
            updateLoggedInState()
        }
        _accountState.asStateFlow()
    }

    private val _loggingIn = MutableStateFlow(false)
    val loggingIn = _loggingIn.asStateFlow()

    private val _loginErrorEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val loginErrorEvent = _loginErrorEvent.asSharedFlow()

    fun logoutFromRetroAchievements() {
        viewModelScope.launch {
            retroAchievementsRepository.logout()
            _accountState.value = RetroAchievementsAccountState.LoggedOut
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loggingIn.value = true
            retroAchievementsRepository.login(username, password)
                .onSuccess {
                    updateLoggedInState()
                }
                .onFailure {
                    _loginErrorEvent.tryEmit(Unit)
                }
            _loggingIn.value = false
        }
    }

    private suspend fun updateLoggedInState() {
        val userAuth = retroAchievementsRepository.getUserAuthentication()
        if (userAuth == null) {
            _accountState.value = RetroAchievementsAccountState.LoggedOut
        } else {
            _accountState.value = RetroAchievementsAccountState.LoggedIn(userAuth.username)
        }
    }
}