package me.magnum.melonds.ui.inputsetup

import me.magnum.melonds.domain.model.InputConfig

data class StatefulInputConfig(val inputConfig: InputConfig, val isBeingConfigured: Boolean = false)