package me.magnum.melonds.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.UUID

@Composable
fun DetachedDialog(
    onDismissRequest: () -> Unit,
    dialogProperties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val parentComposition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val dismiss by rememberUpdatedState(onDismissRequest)
    val id = rememberSaveable { UUID.randomUUID() }

    val fullScreenLayout = remember {
        DetachedDialogLayout(
            composeView = view,
            dialogProperties = dialogProperties,
            uniqueId = id,
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
private class DetachedDialogLayout(
    private val composeView: View,
    private val dialogProperties: DialogProperties,
    uniqueId: UUID
) : AbstractComposeView(composeView.context) {

    private val windowManager = composeView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val params = createLayoutParams()
    private var onDismissListener: (() -> Unit)? = null

    override var shouldCreateCompositionOnAttachedToWindow: Boolean = false
        private set

    init {
        id = android.R.id.content
        setViewTreeLifecycleOwner(composeView.findViewTreeLifecycleOwner())
        setViewTreeViewModelStoreOwner(composeView.findViewTreeViewModelStoreOwner())
        setViewTreeSavedStateRegistryOwner(composeView.findViewTreeSavedStateRegistryOwner())

        setTag(androidx.compose.ui.R.id.compose_view_saveable_id_tag, "DetachedDialog:$uniqueId")
        clipChildren = false
    }

    private var content: @Composable () -> Unit by mutableStateOf({})

    @Composable
    override fun Content() {
        Box(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (dialogProperties.dismissOnClickOutside) {
                            onDismissListener?.invoke()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (dialogProperties.decorFitsSystemWindows) {
                            val widthPx = getDialogWidth()
                            val widthDp = with(LocalDensity.current) { widthPx.toDp() }
                            Modifier.width(widthDp)
                        } else {
                            Modifier.wrapContentSize()
                        }
                    )
                    .pointerInput(Unit) {
                        // Consume taps inside dialog
                        detectTapGestures { }
                    }
            ) {
                content()
            }

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

    private fun getDialogWidth(): Int {
        val metrics = context.resources.displayMetrics
        val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val typedValue = TypedValue()

        val hasDialogTheme = context.theme.resolveAttribute(android.R.attr.alertDialogTheme, typedValue, true)
        val dialogThemeResId = if (hasDialogTheme && typedValue.resourceId != 0) {
            typedValue.resourceId
        } else {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        }

        // Create a temporary theme container and apply the resolved Dialog Theme to simulate being "inside" a Dialog
        val dialogTheme = context.resources.newTheme()
        dialogTheme.setTo(context.theme)
        dialogTheme.applyStyle(dialogThemeResId, true)

        val widthAttr = if (isPortrait) android.R.attr.windowMinWidthMinor else android.R.attr.windowMinWidthMajor
        val resolved = dialogTheme.resolveAttribute(widthAttr, typedValue, true)

        return if (resolved) {
            when (typedValue.type) {
                TypedValue.TYPE_DIMENSION -> typedValue.getDimension(metrics).toInt()
                TypedValue.TYPE_FRACTION -> typedValue.getFraction(metrics.widthPixels.toFloat(), metrics.widthPixels.toFloat()).toInt()
                else -> (metrics.widthPixels * 0.9f).toInt() // Fallback in case of unknown value type
            }
        } else {
            // Fallback in case we can't get the default dialog width
            val density = context.resources.displayMetrics.density
            val minWidth = (280 * density).toInt()
            minWidth.coerceAtMost(metrics.widthPixels)
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            dimAmount = 0.6f
            windowAnimations = android.R.style.Animation_Dialog
            type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            token = composeView.applicationWindowToken
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
        }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (dialogProperties.dismissOnBackPress) {
            if ((event?.keyCode == KeyEvent.KEYCODE_BACK || event?.keyCode == KeyEvent.KEYCODE_ESCAPE) && event.action == KeyEvent.ACTION_DOWN) {
                onDismissListener?.invoke()
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    fun show() {
        windowManager.addView(this, params)
    }

    fun dismiss() {
        destroy()
    }

    private fun destroy() {
        disposeComposition()
        setViewTreeLifecycleOwner(null)
        setViewTreeViewModelStoreOwner(null)
        setViewTreeSavedStateRegistryOwner(null)
        windowManager.removeView(this)
    }
}