package me.magnum.melonds.impl.input

import me.magnum.melonds.domain.model.ControllerConfiguration

interface ControllerConfigurationFactory {
    fun buildDefaultControllerConfiguration(): ControllerConfiguration
}