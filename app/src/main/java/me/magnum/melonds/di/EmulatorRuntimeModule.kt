package me.magnum.melonds.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import me.magnum.melonds.common.PermissionHandler
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.common.runtime.FrameBufferProvider
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.EmulatorManager
import me.magnum.melonds.impl.emulator.AndroidEmulatorManager
import me.magnum.melonds.impl.emulator.SramProvider
import me.magnum.melonds.ui.emulator.camera.LifecycleOwnerProvider

@Module
@InstallIn(ActivityRetainedComponent::class)
object EmulatorRuntimeModule {

    @Provides
    @ActivityRetainedScoped
    fun provideFrameBufferProvider(): FrameBufferProvider {
        return FrameBufferProvider()
    }

    @Provides
    @ActivityRetainedScoped
    fun provideSramProvider(settingsRepository: SettingsRepository, uriHandler: UriHandler): SramProvider {
        return SramProvider(settingsRepository, uriHandler)
    }

    @Provides
    @ActivityRetainedScoped
    fun provideEmulatorLifecycleOwnerProvider(): LifecycleOwnerProvider {
        return LifecycleOwnerProvider()
    }

    @Provides
    @ActivityRetainedScoped
    fun provideEmulatorManager(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
        sramProvider: SramProvider,
        frameBufferProvider: FrameBufferProvider,
        romFileProcessorFactory: RomFileProcessorFactory,
        permissionHandler: PermissionHandler,
        lifecycleOwnerProvider: LifecycleOwnerProvider,
    ): EmulatorManager {
        return AndroidEmulatorManager(
            context,
            settingsRepository,
            sramProvider,
            frameBufferProvider,
            romFileProcessorFactory,
            permissionHandler,
            lifecycleOwnerProvider,
        )
    }
}