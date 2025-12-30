package me.magnum.melonds.domain.services

import me.magnum.melonds.domain.model.layout.DeviceCapability

interface DeviceCapabilityProvider {
    fun getDeviceCapabilities(): List<DeviceCapability>
}