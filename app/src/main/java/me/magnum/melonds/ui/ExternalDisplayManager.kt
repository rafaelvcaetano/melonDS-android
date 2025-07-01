package me.magnum.melonds.ui

/**
 * Simple singleton used to share the current [ExternalPresentation]
 * instance across activities.
 */
object ExternalDisplayManager {
    var presentation: ExternalPresentation? = null
}
