package me.magnum.melonds.domain.model

import android.net.Uri
import java.util.*

data class Background(val id: UUID?, val name: String, val orientation: Orientation, val uri: Uri)