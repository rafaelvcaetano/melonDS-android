package me.magnum.melonds.ui.inputsetup

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityInputSetupBinding
import me.magnum.melonds.databinding.ItemInputBinding
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig
import me.magnum.melonds.ui.inputsetup.InputSetupActivity.InputListAdapter.InputViewHolder
import java.util.*

@AndroidEntryPoint
class InputSetupActivity : AppCompatActivity() {
    companion object {
        private fun getInputName(input: Input): Int {
            return when (input) {
                Input.A -> R.string.input_a
                Input.B -> R.string.input_b
                Input.X -> R.string.input_x
                Input.Y -> R.string.input_y
                Input.LEFT -> R.string.input_left
                Input.RIGHT -> R.string.input_right
                Input.UP -> R.string.input_up
                Input.DOWN -> R.string.input_down
                Input.L -> R.string.input_l
                Input.R -> R.string.input_r
                Input.START -> R.string.input_start
                Input.SELECT -> R.string.input_select
                Input.HINGE -> R.string.input_lid
                Input.PAUSE -> R.string.input_pause
                Input.FAST_FORWARD -> R.string.input_fast_forward
                Input.RESET -> R.string.input_reset
                Input.SWAP_SCREENS -> R.string.input_swap_screens
                Input.QUICK_SAVE -> R.string.input_quick_save
                Input.QUICK_LOAD -> R.string.input_quick_load
                else -> -1
            }
        }
    }

    private lateinit var binding: ActivityInputSetupBinding
    private val viewModel: InputSetupViewModel by viewModels()

    private var waitingForInput = false
    private var inputUnderConfiguration: InputConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInputSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val inputListAdapter = InputListAdapter(object : OnInputConfigClickedListener {
            override fun onInputConfigClicked(inputConfig: InputConfig) {
                if (inputUnderConfiguration != null) {
                    viewModel.stopUpdatingInputConfig(inputUnderConfiguration!!.input)
                }

                viewModel.startUpdatingInputConfig(inputConfig.input)
                waitingForInput = true
                inputUnderConfiguration = inputConfig
            }

            override fun onInputConfigCleared(inputConfig: InputConfig) {
                if (inputUnderConfiguration != null) {
                    if (inputConfig.input === inputUnderConfiguration!!.input) {
                        viewModel.stopUpdatingInputConfig(inputConfig.input)
                    }

                    inputUnderConfiguration = null
                    waitingForInput = false
                }
                viewModel.clearInput(inputConfig.input)
            }
        })
        inputListAdapter.setHasStableIds(true)

        binding.listInput.apply {
            val listLayoutManager = LinearLayoutManager(context)
            layoutManager = listLayoutManager
            addItemDecoration(DividerItemDecoration(context, listLayoutManager.orientation))
            adapter = inputListAdapter
        }
        viewModel.getInputConfig().observe(this) { statefulInputConfigs ->
            val nextInput = inputUnderConfiguration?.let { viewModel.getNextInputToConfigure(it.input) }
            inputListAdapter.setInputList(statefulInputConfigs, nextInput)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (waitingForInput) {
            if (event.keyCode != KeyEvent.KEYCODE_BACK) {
                viewModel.updateInputConfig(inputUnderConfiguration!!.input, event.keyCode)
            } else {
                viewModel.stopUpdatingInputConfig(inputUnderConfiguration!!.input)
            }
            waitingForInput = false
            inputUnderConfiguration = null
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private inner class InputListAdapter(private val inputConfigClickedListener: OnInputConfigClickedListener) : RecyclerView.Adapter<InputViewHolder>() {
        inner class InputViewHolder(private val viewBinding: ItemInputBinding) : RecyclerView.ViewHolder(viewBinding.root) {
            private lateinit var inputConfig: StatefulInputConfig

            fun setInputConfig(config: StatefulInputConfig) {
                inputConfig = config
                val inputNameResource = getInputName(config.inputConfig.input)
                if (inputNameResource == -1)
                    viewBinding.textInputName.text = config.inputConfig.input.toString()
                else
                    viewBinding.textInputName.setText(inputNameResource)

                viewBinding.imageInputClear.isVisible = config.inputConfig.hasKeyAssigned()

                if (config.isBeingConfigured) {
                    viewBinding.textAssignedInputName.setText(R.string.press_any_button)
                } else {
                    if (config.inputConfig.hasKeyAssigned()) {
                        val keyCodeString = KeyEvent.keyCodeToString(config.inputConfig.key)
                        val keyName = keyCodeString.replace("KEYCODE", "").replace("_", " ").trim()
                        viewBinding.textAssignedInputName.text = keyName
                    } else {
                        viewBinding.textAssignedInputName.setText(R.string.not_set)
                    }
                }
            }

            fun setOnClearInputClickListener(listener: View.OnClickListener) {
                viewBinding.imageInputClear.setOnClickListener(listener)
            }

            fun getInputConfig(): InputConfig {
                return inputConfig.inputConfig
            }
        }

        private var recyclerView: RecyclerView? = null
        private var layoutManager: LinearLayoutManager? = null
        private val inputList: ArrayList<StatefulInputConfig> = ArrayList()
        private var nextInput: Input? = null

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            recyclerView.layoutManager?.let {
                if (it is LinearLayoutManager) {
                    layoutManager = it
                }
            }

            this.recyclerView = recyclerView
        }

        fun setInputList(inputList: List<StatefulInputConfig>, nextInput: Input?) {
            this.inputList.clear()
            this.inputList.addAll(inputList)
            this.nextInput = nextInput
            notifyDataSetChanged()
            scrollToNextInputIfNeeded()
        }

        private fun scrollToNextInputIfNeeded() {
            val nextInputIndex = inputList.indexOfFirst { it.inputConfig.input == nextInput }
            if (nextInputIndex >= 0) {
                val lastVisibleIndex = layoutManager?.findLastCompletelyVisibleItemPosition()
                // Add an offset of 1 so that there is still some content visible below the selected item
                if (lastVisibleIndex != null && (nextInputIndex + 1) > lastVisibleIndex) {
                    val smoothScroller = createSmoothScroller(nextInputIndex + 1)
                    layoutManager?.startSmoothScroll(smoothScroller)
                }
            }
        }

        private fun createSmoothScroller(targetPosition: Int): LinearSmoothScroller {
            return object : LinearSmoothScroller(recyclerView!!.context) {
                private val MILLISECONDS_PER_INCH = 100f

                override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                    return MILLISECONDS_PER_INCH / displayMetrics.densityDpi
                }
            }.apply {
                setTargetPosition(targetPosition)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InputViewHolder {
            val viewBinding = ItemInputBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val viewHolder = InputViewHolder(viewBinding)

            viewBinding.root.setOnClickListener { inputConfigClickedListener.onInputConfigClicked(viewHolder.getInputConfig()) }
            viewHolder.setOnClearInputClickListener {
                inputConfigClickedListener.onInputConfigCleared(viewHolder.getInputConfig())
            }

            return viewHolder
        }

        override fun onBindViewHolder(holder: InputViewHolder, position: Int) {
            val inputConfig = inputList[position]
            holder.setInputConfig(inputConfig)

            if (nextInput == inputConfig.inputConfig.input) {
                holder.itemView.requestFocus()
                // Clear next input so that focus is not requested again if the list is updated
                nextInput = null
            }
        }

        override fun getItemId(position: Int): Long {
            return inputList[position].inputConfig.input.ordinal.toLong()
        }

        override fun getItemCount(): Int {
            return inputList.size
        }
    }

    private interface OnInputConfigClickedListener {
        fun onInputConfigClicked(inputConfig: InputConfig)
        fun onInputConfigCleared(inputConfig: InputConfig)
    }
}