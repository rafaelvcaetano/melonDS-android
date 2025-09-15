package me.magnum.melonds.ui

/**
 * Helper utilities for handling the Retroid Dual Screen (RDS) add-on.
 *
 * The add-on's display has a portrait natural orientation. When mounted for
 * landscape usage the content needs to be rotated 90 degrees to the left.
 */
object RdsRotation {

    /** Rotates pairs of [coords] 90 degrees to the left (counter-clockwise). */
    fun rotateLeft(coords: FloatArray) {
        for (i in coords.indices step 2) {
            val x = coords[i]
            val y = coords[i + 1]
            coords[i] = -y
            coords[i + 1] = x
        }
    }
}
