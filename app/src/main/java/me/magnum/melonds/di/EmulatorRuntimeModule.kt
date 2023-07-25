package me.magnum.melonds.di

import android.content.Context
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dagger.multibindings.IntoMap
import me.magnum.melonds.common.PermissionHandler
import me.magnum.melonds.common.camera.BlackDSiCameraSource
import me.magnum.melonds.common.camera.DSiCameraSource
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.common.runtime.FrameBufferProvider
import me.magnum.melonds.common.uridelegates.UriHandler
import me.magnum.melonds.domain.model.camera.DSiCameraSourceType
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.domain.services.EmulatorManager
import me.magnum.melonds.impl.camera.DSiCameraSourceMultiplexer
import me.magnum.melonds.impl.camera.PhysicalDSiCameraSource
import me.magnum.melonds.impl.emulator.AndroidEmulatorManager
import me.magnum.melonds.impl.emulator.LifecycleOwnerProvider
import me.magnum.melonds.impl.emulator.SramProvider


@Module
@InstallIn(ActivityRetainedComponent::class)
object EmulatorRuntimeModule {

    @MapKey
    private annotation class DSiCameraSourceKey(val value: DSiCameraSourceType)

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
    @IntoMap
    @DSiCameraSourceKey(DSiCameraSourceType.BLACK_SCREEN)
    fun provideBlackDSiCameraSource(): DSiCameraSource {
        return BlackDSiCameraSource()
    }

    @Provides
    @IntoMap
    @DSiCameraSourceKey(DSiCameraSourceType.PHYSICAL_CAMERAS)
    fun providePhysicalDSiCameraSource(
        @ApplicationContext context: Context,
        lifecycleOwnerProvider: LifecycleOwnerProvider,
        permissionHandler: PermissionHandler,
    ): DSiCameraSource {
        return PhysicalDSiCameraSource(context, lifecycleOwnerProvider, permissionHandler)
    }

    @Provides
    @ActivityRetainedScoped
    fun provideCameraManagerMultiplexer(
        settingsRepository: SettingsRepository,
        cameraSources: Map<DSiCameraSourceType, @JvmSuppressWildcards DSiCameraSource>,
    ): DSiCameraSourceMultiplexer {
        return DSiCameraSourceMultiplexer(
            dsiCameraSources = cameraSources,
            settingsRepository = settingsRepository,
        )
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
        cameraManagerMultiplexer: DSiCameraSourceMultiplexer,
    ): EmulatorManager {
        return AndroidEmulatorManager(
            context,
            settingsRepository,
            sramProvider,
            frameBufferProvider,
            romFileProcessorFactory,
            permissionHandler,
            cameraManagerMultiplexer,
        )
    }
}