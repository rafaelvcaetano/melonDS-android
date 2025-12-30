package me.magnum.melonds.ui.layouteditor

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.view.Display
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.squareup.picasso.Picasso
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.ui.layouteditor.model.LayoutTarget
import me.magnum.melonds.ui.layouteditor.model.ScreenEditorState

class ExternalLayoutEditorPresentation(
    picasso: Picasso,
    context: Context,
    display: Display,
    private val layoutEditorManagerListener: LayoutEditorManagerView.LayoutEditorManagerListener,
    savedState: ScreenEditorState? = null,
) : Presentation(context, display) {

    /**
     * Wraps the presentation's context to allow views displayed in the presentation to access a [ActivityResultRegistryOwner].
     */
    private class PresentationContextWrapper(
        private val activityContext: Context,
        presentationContext: Context,
    ) : ContextThemeWrapper(presentationContext, activityContext.theme), ActivityResultRegistryOwner by (activityContext as ActivityResultRegistryOwner)

    val layoutEditorManager = LayoutEditorManagerView(
        layoutTarget = LayoutTarget.SECONDARY_SCREEN,
        picasso = picasso,
        initialEditorState = savedState,
        context = PresentationContextWrapper(context, this.context),
        attrs = null,
    )

    init {
        setCancelable(false)

        (context as? LifecycleOwner)?.let { owner ->
            layoutEditorManager.setViewTreeLifecycleOwner(owner)
        }
        (context as? ViewModelStoreOwner)?.let { owner ->
            layoutEditorManager.setViewTreeViewModelStoreOwner(owner)
        }
        (context as? SavedStateRegistryOwner)?.let { owner ->
            layoutEditorManager.setViewTreeSavedStateRegistryOwner(owner)
        }

        layoutEditorManager.apply {
            listener = layoutEditorManagerListener
            setBackgroundColor(Color.BLACK)
            layoutEditorView.setLayoutComponentViewBuilderFactory(EditorLayoutComponentViewBuilderFactory())
        }
        setContentView(layoutEditorManager)
    }

    override fun onBackPressed() {
        layoutEditorManager.openMenu()
    }

    fun instantiateLayout(layout: UILayout) {
        layoutEditorManager.layoutEditorView.instantiateLayout(layout, LayoutTarget.SECONDARY_SCREEN)
    }

    fun saveEditorState(): ScreenEditorState {
        return layoutEditorManager.saveEditorState()
    }
}