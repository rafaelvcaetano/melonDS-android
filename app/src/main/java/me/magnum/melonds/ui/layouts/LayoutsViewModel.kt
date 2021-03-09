package me.magnum.melonds.ui.layouts

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.extensions.addTo
import java.util.*

class LayoutsViewModel @ViewModelInject constructor(layoutsRepository: LayoutsRepository, private val settingsRepository: SettingsRepository) : BaseLayoutsViewModel() {
    init {
        layoutsRepository.getLayouts()
                .subscribeOn(Schedulers.io())
                .subscribe {
                    layoutsLiveData.postValue(it)
                }.addTo(disposables)
    }

    override fun getSelectedLayoutId(): UUID? {
        return settingsRepository.getSelectedLayoutId()
    }

    override fun setSelectedLayout(layoutConfiguration: LayoutConfiguration) {
        layoutConfiguration.id?.let {
            settingsRepository.setSelectedLayoutId(it)
        }
    }
}