package me.magnum.melonds

import me.magnum.melonds.common.UriFileHandler

object MelonDSAndroidInterface {
    external fun setup(uriFileHandler: UriFileHandler)
    external fun getEmulatorGlContext(): Long
    external fun cleanup()
}