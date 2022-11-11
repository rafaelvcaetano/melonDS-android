package me.magnum.melonds.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.*

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
        ViewTreeLifecycleOwner.set(this, ViewTreeLifecycleOwner.get(composeView))
        ViewTreeViewModelStoreOwner.set(this, ViewTreeViewModelStoreOwner.get(composeView))
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
        Log.d("FullScreen", "destroy()")
        disposeComposition()
        ViewTreeLifecycleOwner.set(this, null)
        windowManager.removeViewImmediate(this)
    }
}