package me.magnum.melonds.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.UUID

@Composable
fun FullScreen(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val view = LocalView.current
    val parentComposition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val dismiss by rememberUpdatedState(onDismiss)
    val id = rememberSaveable { UUID.randomUUID() }

    val fullScreenLayout = remember {
        FullScreenLayout(
            view,
            id
        ).apply {
            setContent(parentComposition) {
                currentContent()
            }
            setOnDismissListener(dismiss)
        }
    }

    DisposableEffect(fullScreenLayout) {
        fullScreenLayout.show()
        onDispose { fullScreenLayout.dismiss() }
    }
}

@SuppressLint("ViewConstructor")
private class FullScreenLayout(
    private val composeView: View,
    uniqueId: UUID
) : AbstractComposeView(composeView.context) {

    private val windowManager = composeView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val params = createLayoutParams()
    private var visible = MutableTransitionState(false).apply { targetState = true }
    private var onDismissListener: (() -> Unit)? = null

    override var shouldCreateCompositionOnAttachedToWindow: Boolean = false
        private set

    init {
        id = android.R.id.content
        setViewTreeLifecycleOwner(composeView.findViewTreeLifecycleOwner())
        setViewTreeViewModelStoreOwner(composeView.findViewTreeViewModelStoreOwner())
        setViewTreeSavedStateRegistryOwner(composeView.findViewTreeSavedStateRegistryOwner())

        setTag(androidx.compose.ui.R.id.compose_view_saveable_id_tag, "CustomLayout:$uniqueId")
    }

    private var content: @Composable () -> Unit by mutableStateOf({})

    @Composable
    override fun Content() {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        ) {
            content()

            DisposableEffect(null) {
                onDispose {
                    destroy()
                }
            }
        }
    }

    fun setContent(parent: CompositionContext, content: @Composable () -> Unit) {
        setParentCompositionContext(parent)
        this.content = content
        shouldCreateCompositionOnAttachedToWindow = true
    }

    fun setOnDismissListener(listener: (() -> Unit)?) {
        onDismissListener = listener
    }

    private fun createLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            token = composeView.applicationWindowToken
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            format = PixelFormat.TRANSLUCENT
        }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
            onDismissListener?.invoke()
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    fun show() {
        windowManager.addView(this, params)
    }

    fun dismiss() {
        visible.targetState = false
    }

    private fun destroy() {
        disposeComposition()
        setViewTreeLifecycleOwner(null)
        windowManager.removeViewImmediate(this)
    }
}