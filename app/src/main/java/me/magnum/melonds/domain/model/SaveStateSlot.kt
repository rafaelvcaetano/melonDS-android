package me.magnum.melonds.domain.model

import android.net.Uri
import java.util.*

data class SaveStateSlot(val slot: Int, val exists: Boolean, val lastUsedDate: Date?, val screenshot: Uri?)