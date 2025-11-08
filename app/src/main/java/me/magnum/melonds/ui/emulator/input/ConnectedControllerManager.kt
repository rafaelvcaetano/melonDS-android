package me.magnum.melonds.ui.emulator.input

import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig
import me.magnum.melonds.extensions.removeFirst
import me.magnum.melonds.ui.emulator.model.ConnectedControllersState

class ConnectedControllerManager : InputManager.InputDeviceListener {

    private var managedControllers = MutableStateFlow<List<InputDevice>>(emptyList())
    private val currentControllerConfiguration = MutableStateFlow<ControllerConfiguration?>(null)

    val controllersState: Flow<ConnectedControllersState> by lazy {
        combine(managedControllers, currentControllerConfiguration) { managedControllers, controllerConfiguration ->
            if (managedControllers.isEmpty() || controllerConfiguration == null) {
                ConnectedControllersState.NoControllers
            } else {
                if (areSystemButtonsHandledByConnectedControllers(managedControllers, controllerConfiguration)) {
                    val assignedInputs = controllerConfiguration.inputMapper.filter {
                        val controllerAssignments = listOfNotNull(
                            it.assignment.takeUnless { it == InputConfig.Assignment.None },
                            it.altAssignment.takeUnless { it == InputConfig.Assignment.None },
                        )
                        controllerAssignments.any { assignment ->
                            when(assignment) {
                                is InputConfig.Assignment.Axis -> managedControllers.any { it.getMotionRange(assignment.axisCode) != null }
                                is InputConfig.Assignment.Key -> managedControllers.any { it.hasKeys(assignment.keyCode)[0] }
                                InputConfig.Assignment.None -> false
                            }
                        }
                    }.map { it.input }
                    ConnectedControllersState.ControllersConnected(assignedInputs)
                } else {
                    ConnectedControllersState.NoControllers
                }
            }
        }
    }

    fun startTrackingControllers() {
        initializeManagedInputControllers()
    }

    fun stopTrackingControllers() {
        managedControllers.value = emptyList()
    }

    fun setCurrentControllerConfiguration(controllerConfiguration: ControllerConfiguration) {
        currentControllerConfiguration.value = controllerConfiguration
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        InputDevice.getDevice(deviceId)?.let { device ->
            if (isValidController(device)) {
                managedControllers.update {
                    it.toMutableList().apply {
                        add(device)
                    }
                }
            }
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        managedControllers.update {
            val deviceIndex = it.indexOfFirst { it.id == deviceId }
            if (deviceIndex >= 0) {
                it.toMutableList().apply {
                    removeAt(deviceIndex)
                }
            } else {
                it
            }
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        managedControllers.update {
            val mutableList = it.toMutableList()
            mutableList.removeFirst { it.id == deviceId }
            InputDevice.getDevice(deviceId)?.let {
                if (isValidController(it)) {
                    mutableList.add(it)
                }
            }
            mutableList
        }
    }

    private fun initializeManagedInputControllers() {
        managedControllers.update {
            val deviceList = mutableListOf<InputDevice>()
            InputDevice.getDeviceIds().forEach {
                InputDevice.getDevice(it)?.let { device ->
                    if (isValidController(device)) {
                        deviceList.add(device)
                    }
                }
            }
            deviceList
        }
    }

    private fun isValidController(device: InputDevice): Boolean {
        val isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            device.isEnabled
        } else {
            true
        }
        return isEnabled && !device.isVirtual && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK))
    }

    /**
     * Check if the connected [controllers], in conjunction, have bindings for the system buttons (A B X Y, D-Pad, Start, Select, L, R) as set by the
     * [controllerConfiguration]. Checking controllers as a whole group is beneficial due to split controllers (like JoyCons) which are always likely to be used as a pair and,
     * as such, an individual controller would never have all the required bindings.
     */
    private fun areSystemButtonsHandledByConnectedControllers(controllers: List<InputDevice>, controllerConfiguration: ControllerConfiguration): Boolean {
        return Input.SYSTEM_BUTTONS.all {
            val controllerSupportsAssignment = controllerConfiguration.getInputAssignments(it)?.any { assignment ->
                controllers.any { device ->
                    when (assignment) {
                        is InputConfig.Assignment.Axis -> device.getMotionRange(assignment.axisCode) != null
                        is InputConfig.Assignment.Key -> device.hasKeys(assignment.keyCode)[0]
                        InputConfig.Assignment.None -> false
                    }
                }
            }

            controllerSupportsAssignment == true
        }
    }
}