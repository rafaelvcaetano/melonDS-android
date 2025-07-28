package me.magnum.melonds.ui.inputsetup

import android.graphics.Color
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.InputConfig
import me.magnum.melonds.ui.inputsetup.ui.InputSetupScreen
import me.magnum.melonds.ui.theme.MelonTheme
import kotlin.math.absoluteValue

@AndroidEntryPoint
class InputSetupActivity : AppCompatActivity() {

    private val viewModel: InputSetupViewModel by viewModels()

    private val referenceAxisValues = mutableMapOf<Int, Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            MelonTheme {
                InputSetupScreen(
                    viewModel = viewModel,
                    onBackClick = ::onNavigateUp,
                )
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.inputUnderAssignment.collect {
                    if (it != null) {
                        // A new assignment has started. Reset reference values
                        referenceAxisValues.clear()
                        InputDevice.getDeviceIds().forEach { deviceId ->
                            InputDevice.getDevice(deviceId)?.motionRanges?.forEach { range ->
                                if (range.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)) {
                                    referenceAxisValues[range.axis] = 0f
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (viewModel.inputUnderAssignment.value != null && event.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)) {
            if (event.action == MotionEvent.ACTION_MOVE) {
                val detectedAxis = referenceAxisValues.firstNotNullOfOrNull {
                    val currentValue = event.getAxisValue(it.key)
                    val delta = (currentValue - it.value).absoluteValue
                    if (delta >= 0.5f) {
                        it.key
                    } else {
                        null
                    }
                }

                if (detectedAxis != null) {
                    val initialValue = referenceAxisValues[detectedAxis]!!
                    val currentValue = event.getAxisValue(detectedAxis)
                    val delta = currentValue - initialValue
                    val direction = if (delta > 0f) {
                        InputConfig.Assignment.Axis.Direction.POSITIVE
                    } else {
                        InputConfig.Assignment.Axis.Direction.NEGATIVE
                    }
                    viewModel.updateInputAssignedAxis(detectedAxis, direction)
                }
                return true
            }
        }

        return super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.inputUnderAssignment.value != null && event.action == KeyEvent.ACTION_DOWN) {
            viewModel.updateInputAssignedKey(event.keyCode)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}