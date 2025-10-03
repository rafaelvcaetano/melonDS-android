package me.magnum.melonds.ui

/**
 * Simple singleton used to share the current [ExternalPresentation]
 * instance across activities.
 */
object ExternalDisplayManager {
    var presentation: ExternalPresentation? = null

    /**
     * Dismisses the current [ExternalPresentation], if any, and clears the
     * stored reference. This effectively detaches content from the external
     * display.
     */
    fun detach() {
        presentation?.dismiss()
        presentation = null
    }
}
