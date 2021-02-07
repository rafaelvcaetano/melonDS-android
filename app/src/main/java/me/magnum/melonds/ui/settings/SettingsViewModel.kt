package me.magnum.melonds.ui.settings

import android.net.Uri
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import me.magnum.melonds.domain.repositories.CheatsRepository

class SettingsViewModel @ViewModelInject constructor(private val cheatsRepository: CheatsRepository) : ViewModel() {
    fun importCheatsDatabase(databaseUri: Uri) {
        cheatsRepository.importCheats(databaseUri)
    }
}