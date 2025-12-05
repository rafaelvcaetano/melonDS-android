package me.magnum.melonds.ui.layouteditor

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.widget.ImageView
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.extensions.setBackgroundMode

class LayoutEditorPreviewPresentation(
    context: Context,
    display: Display,
    private val picasso: Picasso,
) : Presentation(context, display) {

    private lateinit var backgroundView: ImageView
    private lateinit var layoutView: LayoutPreviewCanvasView

    private var pendingLayout: UILayout? = null
    private var pendingSourceSize: Point? = null
    private var pendingBackground: RuntimeBackground? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.presentation_layout_editor_preview)
        backgroundView = findViewById(R.id.image_preview_background)
        layoutView = findViewById(R.id.view_layout_preview)
        pendingBackground?.let { loadBackground(it) }
        layoutView.post { applyPendingLayout() }
    }

    fun updateLayout(layout: UILayout?, sourceSize: Point?) {
        pendingLayout = layout
        pendingSourceSize = sourceSize
        applyPendingLayout()
    }

    fun updateBackground(background: RuntimeBackground?) {
        pendingBackground = background
        if (!::backgroundView.isInitialized) {
            return
        }
        loadBackground(background)
    }

    private fun loadBackground(background: RuntimeBackground?) {
        picasso.cancelRequest(backgroundView)
        if (background == null || background.background?.uri == null) {
            backgroundView.setImageDrawable(null)
            return
        }

        val mode = background.mode
        picasso.load(background.background.uri).into(backgroundView, object : Callback {
            override fun onSuccess() {
                backgroundView.setBackgroundMode(mode)
            }

            override fun onError(e: java.lang.Exception?) {
                backgroundView.setImageDrawable(null)
            }
        })
    }

    private fun applyPendingLayout() {
        if (!::layoutView.isInitialized) {
            return
        }

        layoutView.submitLayout(pendingLayout, pendingSourceSize)
    }
}
