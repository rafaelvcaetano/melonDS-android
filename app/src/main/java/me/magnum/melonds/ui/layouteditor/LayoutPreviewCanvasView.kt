package me.magnum.melonds.ui.layouteditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.UILayout

class LayoutPreviewCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private data class ComponentRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val component: LayoutComponent,
    )

    private val screenFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2962FF")
        alpha = 140
        style = Paint.Style.FILL
    }
    private val bottomScreenFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D50000")
        alpha = 160
        style = Paint.Style.FILL
    }
    private val componentFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00C853")
        alpha = 140
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
    }

    private var pendingLayout: UILayout? = null
    private var pendingSourceSize: Point? = null
    private var scaledComponents: List<ComponentRect> = emptyList()

    fun submitLayout(layout: UILayout?, sourceSize: Point?) {
        pendingLayout = layout
        pendingSourceSize = sourceSize
        recomputeScaledComponents()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            recomputeScaledComponents()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        scaledComponents.forEach {
            val paint = when (it.component) {
                LayoutComponent.TOP_SCREEN -> screenFill
                LayoutComponent.BOTTOM_SCREEN -> bottomScreenFill
                else -> componentFill
            }
            canvas.drawRect(it.left, it.top, it.right, it.bottom, paint)
            canvas.drawRect(it.left, it.top, it.right, it.bottom, borderPaint)
        }
    }

    private fun recomputeScaledComponents() {
        val layout = pendingLayout
        val source = pendingSourceSize
        if (layout?.components == null || source == null || width <= 0 || height <= 0) {
            scaledComponents = emptyList()
            invalidate()
            return
        }
        if (source.x <= 0 || source.y <= 0) {
            scaledComponents = emptyList()
            invalidate()
            return
        }
        val scaleX = width.toFloat() / source.x.toFloat()
        val scaleY = height.toFloat() / source.y.toFloat()
        scaledComponents = layout.components!!.map { component ->
            val rect = component.rect
            ComponentRect(
                left = rect.x * scaleX,
                top = rect.y * scaleY,
                right = (rect.x + rect.width) * scaleX,
                bottom = (rect.y + rect.height) * scaleY,
                component = component.component,
            )
        }
        invalidate()
    }
}
