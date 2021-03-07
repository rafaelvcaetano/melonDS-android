package me.magnum.melonds.ui.emulator.input

import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point
import kotlin.math.pow

abstract class MultiButtonInputHandler(inputListener: IInputListener) : BaseInputHandler(inputListener) {
    private var areDimensionsInitialized = false
    private val buttonCircles = mutableListOf<ButtonCircle>()
    private val pressedInputs = mutableListOf<Input>()
    private val newPressedInputs = mutableListOf<Input>()
    // Reusable input list to avoid memory allocations
    private val tempInputList = mutableListOf<Input>()

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!areDimensionsInitialized) {
            initDimensions(v.width, v.height)
            areDimensionsInitialized = true
        }

        newPressedInputs.clear()

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                buttonCircles.forEach {
                    if (it.containsPoint(event.x, event.y)) {
                        newPressedInputs.add(it.input)
                    }
                }
            }
        }

        tempInputList.clear()
        pressedInputs.filterNotTo(tempInputList) {
            it in newPressedInputs
        }.forEach {
            inputListener.onKeyReleased(it)
        }

        tempInputList.clear()
        newPressedInputs.filterNotTo(tempInputList) {
            it in pressedInputs
        }.forEach {
            inputListener.onKeyPress(it)
        }

        pressedInputs.clear()
        pressedInputs.addAll(newPressedInputs)

        return true
    }

    private fun initDimensions(viewWidth: Int, viewHeight: Int) {
        val radiusSquared = (viewWidth * 195f / 512f).pow(2)
        val pointToLocal: (Float, Float) -> Point = { x, y ->
            Point().apply {
                this.x = (viewWidth * x / 512f).toInt()
                this.y = (viewHeight * y / 512f).toInt()
            }
        }

        // Each circle is placed on the side of each button, near the edge of the whole image. This allows for a margin of error and the circles also intersect, allowing
        // multiple buttons to be pressed at the same time
        buttonCircles.add(ButtonCircle(pointToLocal(512f - 16, 256f), radiusSquared, getRightInput()))
        buttonCircles.add(ButtonCircle(pointToLocal(256f, 512f - 16), radiusSquared, getBottomInput()))
        buttonCircles.add(ButtonCircle(pointToLocal(256f, 16f), radiusSquared, getTopInput()))
        buttonCircles.add(ButtonCircle(pointToLocal(16f, 256f), radiusSquared, getLeftInput()))
    }

    private data class ButtonCircle(val center: Point, val radiusSquared: Float, val input: Input) {
        fun containsPoint(pointX: Float, pointY: Float): Boolean {
            return (pointX - center.x).pow(2) + (pointY - center.y).pow(2) <= radiusSquared
        }
    }

    abstract fun getTopInput(): Input
    abstract fun getLeftInput(): Input
    abstract fun getBottomInput(): Input
    abstract fun getRightInput(): Input
}