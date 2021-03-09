package me.magnum.melonds.ui.layouts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.domain.model.LayoutConfiguration
import java.util.*

abstract class BaseLayoutsViewModel : ViewModel() {
    protected val disposables = CompositeDisposable()
    protected val layoutsLiveData = MutableLiveData<List<LayoutConfiguration>>()

    fun getLayouts(): LiveData<List<LayoutConfiguration>> {
        return layoutsLiveData
    }

    abstract fun getSelectedLayoutId(): UUID?
    abstract fun setSelectedLayout(layoutConfiguration: LayoutConfiguration)

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}