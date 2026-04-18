package me.magnum.melonds.impl.camera

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.magnum.melonds.common.camera.CameraType
import me.magnum.melonds.common.camera.DSiCameraSource
import me.magnum.melonds.domain.model.camera.DSiCameraSourceType
import me.magnum.melonds.domain.repositories.SettingsRepository

class DSiCameraSourceMultiplexer(
    private val dsiCameraSources: Map<DSiCameraSourceType, DSiCameraSource>,
    private val settingsRepository: SettingsRepository,
) : DSiCameraSource {

    private sealed class CurrentCameraState {
        data class Running(val activeCamera: CameraType) : CurrentCameraState()
        object Stopped : CurrentCameraState()
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main.immediate)
    private var activeDSiCameraSource: DSiCameraSource? = null
    private var currentCameraState: CurrentCameraState = CurrentCameraState.Stopped

    init {
        coroutineScope.launch {
            settingsRepository.observeDSiCameraSource().collect { cameraSourceType ->
                val cameraState = currentCameraState
                if (cameraState is CurrentCameraState.Running) {
                    activeDSiCameraSource?.stopCamera(cameraState.activeCamera)
                }

                val newCameraSource = dsiCameraSources[cameraSourceType]?.takeIf { it.isAvailable() } ?: dsiCameraSources[DSiCameraSourceType.BLACK_SCREEN]
                activeDSiCameraSource = newCameraSource?.also {
                    if (cameraState is CurrentCameraState.Running) {
                        it.startCamera(cameraState.activeCamera)
                    }
                }
            }
        }
    }

    override fun isAvailable() = true

    override fun startCamera(camera: CameraType) {
        currentCameraState = CurrentCameraState.Running(camera)
        activeDSiCameraSource?.startCamera(camera)
    }

    override fun stopCamera(camera: CameraType) {
        currentCameraState = CurrentCameraState.Stopped
        activeDSiCameraSource?.stopCamera(camera)
    }

    override fun captureFrame(camera: CameraType, buffer: ByteArray, width: Int, height: Int, isYuv: Boolean) {
        activeDSiCameraSource?.captureFrame(camera, buffer, width, height, isYuv)
    }

    override fun dispose() {
        activeDSiCameraSource = null
        dsiCameraSources.values.forEach {
            it.dispose()
        }
        coroutineScope.cancel()
    }

    fun stopCurrentCameraSource() {
        val cameraState = currentCameraState
        if (cameraState is CurrentCameraState.Running) {
            activeDSiCameraSource?.stopCamera(cameraState.activeCamera)
        }
    }
}