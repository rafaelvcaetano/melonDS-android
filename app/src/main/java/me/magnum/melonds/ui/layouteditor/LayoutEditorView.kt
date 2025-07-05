package me.magnum.melonds.ui.layouteditor

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.ui.common.LayoutComponentView
import me.magnum.melonds.ui.common.LayoutView
import kotlin.math.*

typealias ViewSelectedListener = (
                                  view: LayoutComponentView,
                                  widthScale: Float,
                                  heightScale: Float,
                                  maxWidth: Int,
                                  maxHeight: Int,
                                  minSize: Int
) -> Unit

class LayoutEditorView(context: Context, attrs: AttributeSet?) : LayoutView(context, attrs) {
    enum class Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    private var onViewSelectedListener: ViewSelectedListener? = null
    private var onViewDeselectedListener: ((LayoutComponentView) -> Unit)? = null
    private var otherClickListener: OnClickListener? = null
    private val defaultComponentWidth by lazy { screenUnitsConverter.dpToPixels(100f).toInt() }
    private val minComponentSize by lazy { screenUnitsConverter.dpToPixels(30f).toInt() }
    private var selectedView: LayoutComponentView? = null
    private var selectedViewAnchor = Anchor.TOP_LEFT
    private var modifiedByUser = false

    init {
        super.setOnClickListener {
            if (selectedView != null) {
                deselectCurrentView()
            } else {
                otherClickListener?.onClick(it)
            }
        }
    }

    override fun instantiateLayout(layoutConfiguration: UILayout) {
        super.instantiateLayout(layoutConfiguration)
        modifiedByUser = false
    }

    fun setOnViewSelectedListener(listener: ViewSelectedListener) {
        onViewSelectedListener = listener
    }

    fun setOnViewDeselectedListener(listener: (LayoutComponentView) -> Unit) {
        onViewDeselectedListener = listener
    }

    fun addLayoutComponent(component: LayoutComponent) {
        val componentBuilder = viewBuilderFactory.getLayoutComponentViewBuilder(component)
        val componentHeight = defaultComponentWidth / componentBuilder.getAspectRatio()
        val componentView = addPositionedLayoutComponent(PositionedLayoutComponent(Rect(0, 0, defaultComponentWidth, componentHeight.toInt()), component))
        views[component] = componentView
        modifiedByUser = true
    }

    fun isModifiedByUser(): Boolean {
        return modifiedByUser
    }

    fun buildCurrentLayout(): List<PositionedLayoutComponent> {
        return views.values.map {
            PositionedLayoutComponent(it.getRect(), it.component, it.baseAlpha, it.onTop)
        }
    }

    fun handleKeyDown(event: KeyEvent): Boolean {
        val currentlySelectedView = selectedView ?: return false

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> dragView(currentlySelectedView, 0f, -1f)
            KeyEvent.KEYCODE_DPAD_DOWN -> dragView(currentlySelectedView, 0f, 1f)
            KeyEvent.KEYCODE_DPAD_LEFT -> dragView(currentlySelectedView, -1f, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> dragView(currentlySelectedView, 1f, 0f)
            else -> return false
        }

        return true
    }

    override fun setOnClickListener(l: OnClickListener?) {
        otherClickListener = l
    }

    override fun onLayoutComponentViewAdded(layoutComponentView: LayoutComponentView) {
        super.onLayoutComponentViewAdded(layoutComponentView)
        setupDragHandler(layoutComponentView)
        layoutComponentView.view.alpha = 0.5f
        layoutComponentView.setHighlighted(false)
    }

    private fun setupDragHandler(layoutComponentView: LayoutComponentView) {
        layoutComponentView.view.setOnTouchListener(object : OnTouchListener {
            private var dragging = false

            private var downOffsetX = -1f
            private var downOffsetY = -1f

            override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
                if (view == null)
                    return false

                if (selectedView != null) {
                    deselectCurrentView()
                }

                return when (motionEvent?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downOffsetX = motionEvent.x
                        downOffsetY = motionEvent.y
                        view.alpha = 1f
                        layoutComponentView.setHighlighted(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) {
                            val distance = sqrt((motionEvent.x - downOffsetX).pow(2f) + (motionEvent.y - downOffsetY).pow(2f))
                            if (distance >= 25) {
                                dragging = true
                            }
                        } else {
                            dragView(layoutComponentView, motionEvent.x - downOffsetX, motionEvent.y - downOffsetY)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) {
                            selectView(layoutComponentView)
                        } else {
                            view.alpha = 0.5f
                            layoutComponentView.setHighlighted(false)
                            dragging = false
                        }
                        true
                    }
                    else -> false
                }
            }
        })
    }

    private fun selectView(view: LayoutComponentView) {
        val anchorDistances = mutableMapOf<Anchor, Double>()
        anchorDistances[Anchor.TOP_LEFT] = view.getPosition().x.toDouble().pow(2) + view.getPosition().y.toDouble().pow(2)
        anchorDistances[Anchor.TOP_RIGHT] = (width - (view.getPosition().x + view.getWidth())).toDouble().pow(2) + view.getPosition().y.toDouble().pow(2)
        anchorDistances[Anchor.BOTTOM_LEFT] = view.getPosition().x.toDouble().pow(2) + (height - (view.getPosition().y + view.getHeight())).toDouble().pow(2)
        anchorDistances[Anchor.BOTTOM_RIGHT] = (width - (view.getPosition().x + view.getWidth())).toDouble().pow(2) + (height - (view.getPosition().y + view.getHeight())).toDouble().pow(2)

        var anchor = Anchor.TOP_LEFT
        var minDistance = Double.MAX_VALUE
        anchorDistances.keys.forEach {
            if (anchorDistances[it]!! < minDistance) {
                minDistance = anchorDistances[it]!!
                anchor = it
            }
        }

        selectedViewAnchor = anchor
        selectedView = view

        val widthScale = (view.getWidth() - minComponentSize) / (width - minComponentSize).toFloat()
        val heightScale = (view.getHeight() - minComponentSize) / (height - minComponentSize).toFloat()
        onViewSelectedListener?.invoke(view, widthScale, heightScale, width, height, minComponentSize)
    }

    private fun deselectCurrentView() {
        selectedView?.let {
            it.view.alpha = 0.5f
            it.setHighlighted(false)
            onViewDeselectedListener?.invoke(it)
        }
        selectedView = null
    }

    fun deleteSelectedView() {
        val currentlySelectedView = selectedView ?: return
        deselectCurrentView()
        removeView(currentlySelectedView.view)
        views.remove(currentlySelectedView.component)
    }

    private fun dragView(view: LayoutComponentView, offsetX: Float, offsetY: Float) {
        val currentPosition = view.getPosition()
        val finalX = min(max(currentPosition.x + offsetX, 0f), width - view.getWidth().toFloat())
        val finalY = min(max(currentPosition.y + offsetY, 0f), height - view.getHeight().toFloat())
        view.setPosition(Point(finalX.toInt(), finalY.toInt()))
        modifiedByUser = true
    }

    fun setSelectedViewAlpha(alpha: Float) {
        selectedView?.let {
            it.baseAlpha = alpha
            it.setHighlighted(true)
            modifiedByUser = true
        }
    }

    fun setSelectedScreenOnTop(onTop: Boolean) {
        selectedView?.let {
            it.onTop = onTop
            rearrangeScreens()
            modifiedByUser = true
        }
    }

    private fun rearrangeScreens() {
        val topScreen = views[LayoutComponent.TOP_SCREEN]
        val bottomScreen = views[LayoutComponent.BOTTOM_SCREEN]
        if (topScreen != null && bottomScreen != null) {
            removeView(topScreen.view)
            removeView(bottomScreen.view)
            if (topScreen.onTop) {
                addView(bottomScreen.view, 0)
                addView(topScreen.view, 0)
            } else if (bottomScreen.onTop) {
                addView(topScreen.view, 0)
                addView(bottomScreen.view, 0)
            } else {
                addView(bottomScreen.view, 0)
                addView(topScreen.view, 0)
            }
        }
    }

    fun scaleSelectedView(widthScale: Float, heightScale: Float) {
        val currentlySelectedView = selectedView ?: return

        val newViewWidth = ((width - minComponentSize) * widthScale + minComponentSize).roundToInt()
        val newViewHeight = ((height - minComponentSize) * heightScale + minComponentSize).roundToInt()

        val viewPosition = currentlySelectedView.getPosition()
        var viewX: Int
        var viewY: Int

        if (selectedViewAnchor == Anchor.TOP_LEFT) {
            viewX = viewPosition.x
            viewY = viewPosition.y
            if (viewX + newViewWidth > width) {
                viewX = width - newViewWidth
            }
            if (viewY + newViewHeight > height) {
                viewY = height - newViewHeight
            }
        } else if (selectedViewAnchor == Anchor.TOP_RIGHT) {
            viewX = viewPosition.x + currentlySelectedView.getWidth() - newViewWidth
            viewY = viewPosition.y
            if (viewX < 0) {
                viewX = 0
            }
            if (viewY + newViewHeight > height) {
                viewY = height - newViewHeight
            }
        } else if (selectedViewAnchor == Anchor.BOTTOM_LEFT) {
            viewX = viewPosition.x
            viewY = viewPosition.y + currentlySelectedView.getHeight() - newViewHeight
            if (viewX + newViewWidth > width) {
                viewX = width - newViewWidth
            }
            if (viewY < 0) {
                viewY = 0
            }
        } else {
            viewX = viewPosition.x + currentlySelectedView.getWidth() - newViewWidth
            viewY = viewPosition.y + currentlySelectedView.getHeight() - newViewHeight
            if (viewX < 0) {
                viewX = 0
            }
            if (viewY < 0) {
                viewY = 0
            }
        }
        currentlySelectedView.setPositionAndSize(Point(viewX, viewY), newViewWidth, newViewHeight)
        modifiedByUser = true
    }
}