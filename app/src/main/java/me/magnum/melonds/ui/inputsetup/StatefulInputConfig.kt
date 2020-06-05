package me.magnum.melonds.ui.inputsetup

import me.magnum.melonds.model.InputConfig

data class StatefulInputConfig(val inputConfig: InputConfig, var isBeingConfigured:Boolean = false)