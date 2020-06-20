package me.magnum.melonds.model

import java.util.*

data class SaveStateSlot(val slot: Int, val exists: Boolean, val path: String, val lastUsedDate: Date?)