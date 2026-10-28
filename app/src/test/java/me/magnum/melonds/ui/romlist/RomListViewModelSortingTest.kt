package me.magnum.melonds.ui.romlist

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.domain.model.SortingMode
import me.magnum.melonds.domain.model.SortingOrder
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.impl.RomIconProvider
import me.magnum.melonds.impl.SharedPreferencesSettingsRepository
import me.magnum.melonds.impl.input.ControllerConfigurationFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class RomListViewModelSortingTest {
    @Test fun toggledSortingOrderSurvivesViewModelAndSettingsRecreation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("rom-sorting-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        // Controller/render warmup is unrelated to sorting; do not launch application background work.
        val preferenceScope = CoroutineScope(SupervisorJob().apply { cancel() })
        fun settings() = SharedPreferencesSettingsRepository(context, preferences, unused<ControllerConfigurationFactory>(), Json, unused<UriHandler>(), preferenceScope)
        val roms = listOf("Alpha", "Zulu").map { Rom(it, "Fixture", "$it.nds", Uri.parse("content://roms/$it.nds"), null, RomConfig.default(), null, false, "") }
        val repository = Proxy.newProxyInstance(RomsRepository::class.java.classLoader, arrayOf(RomsRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "getRoms" -> flowOf(roms)
                "getRomScanningStatus" -> MutableStateFlow(RomScanningStatus.NOT_SCANNING)
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as RomsRepository
        val viewModels = mutableListOf<RomListViewModel>()
        fun viewModel(settings: SharedPreferencesSettingsRepository) = RomListViewModel(repository, settings,
            RomIconProvider(context, unused<RomFileProcessorFactory>()), UriPermissionManager(context), DirectoryAccessValidator(context)
        ).also { viewModels += it }

        try {
            val initialSettings = settings()
            val initial = viewModel(initialSettings)
            runCurrent()
            assertEquals(listOf("Alpha", "Zulu"), initial.roms.value!!.map { it.name })
            initial.setRomSorting(SortingMode.ALPHABETICALLY)
            runCurrent()
            assertEquals(listOf("Zulu", "Alpha"), initial.roms.value!!.map { it.name })
            initial.viewModelScope.cancel()

            val restoredSettings = settings()
            val restored = viewModel(restoredSettings)
            runCurrent()
            assertEquals(listOf("Zulu", "Alpha"), restored.roms.value!!.map { it.name })
            assertEquals(SortingOrder.DESCENDING, restoredSettings.getRomSortingOrder())
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
            preferenceScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("Unexpected dependency call: ${method.name}")
    } as T
}
