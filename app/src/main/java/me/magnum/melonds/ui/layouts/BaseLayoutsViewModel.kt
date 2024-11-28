package me.magnum.melonds.ui.layouts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.domain.model.layout.LayoutConfiguration
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.extensions.addTo
import java.util.*

abstract class BaseLayoutsViewModel(protected val layoutsRepository: LayoutsRepository) : ViewModel() {
    protected val disposables = CompositeDisposable()
    protected val layoutsLiveData = MutableLiveData<List<LayoutConfiguration>>()

    fun getLayouts(): LiveData<List<LayoutConfiguration>> {
        return layoutsLiveData
    }

    fun addLayout(layout: LayoutConfiguration) {
        layoutsRepository.saveLayout(layout)
    }

    fun deleteLayout(layout: LayoutConfiguration) {
        layoutsRepository.deleteLayout(layout)
                .subscribe()
                .addTo(disposables)
    }

    abstract fun getSelectedLayoutId(): UUID?
    abstract fun setSelectedLayoutId(id: UUID?)

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}