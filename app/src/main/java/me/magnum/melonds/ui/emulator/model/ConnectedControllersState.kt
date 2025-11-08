package me.magnum.melonds.ui.emulator.model

import me.magnum.melonds.domain.model.Input

sealed class ConnectedControllersState {
    data object NoControllers : ConnectedControllersState()
    data class ControllersConnected(val assignedInputs: List<Input>) : ConnectedControllersState()
}