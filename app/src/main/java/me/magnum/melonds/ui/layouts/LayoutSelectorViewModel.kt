package me.magnum.melonds.ui.layouts

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.schedulers.Schedulers
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.extensions.addTo
import java.util.*
import javax.inject.Inject

@HiltViewModel
class LayoutSelectorViewModel @Inject constructor(layoutsRepository: LayoutsRepository, savedStateHandle: SavedStateHandle) : BaseLayoutsViewModel(layoutsRepository) {
    private var currentSelectedLayout: UUID?

    init {
        currentSelectedLayout = savedStateHandle.get<String?>(LayoutSelectorActivity.KEY_SELECTED_LAYOUT_ID)?.let { UUID.fromString(it) }

        layoutsRepository.getLayouts()
                .subscribeOn(Schedulers.io())
                .subscribe {
                    val layoutList = it.toMutableList()
                    layoutList.add(0, layoutsRepository.getGlobalLayoutPlaceholder())
                    layoutsLiveData.postValue(layoutList)
                }.addTo(disposables)
    }

    override fun getSelectedLayoutId(): UUID? {
        return currentSelectedLayout
    }

    override fun setSelectedLayoutId(id: UUID?) {
        currentSelectedLayout = id
    }
}