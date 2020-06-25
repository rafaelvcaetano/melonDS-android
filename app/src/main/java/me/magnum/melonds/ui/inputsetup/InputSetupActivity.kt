package me.magnum.melonds.ui.inputsetup

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_input_setup.*
import kotlinx.android.synthetic.main.item_input.view.*
import me.magnum.melonds.R
import me.magnum.melonds.ServiceLocator
import me.magnum.melonds.model.Input
import me.magnum.melonds.model.InputConfig
import me.magnum.melonds.ui.inputsetup.InputSetupActivity.InputListAdapter.InputViewHolder
import java.util.*

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
                else -> -1
            }
        }
    }

    private val viewModel: InputSetupViewModel by viewModels { ServiceLocator[ViewModelProvider.Factory::class] }

    private var waitingForInput = false
    private var inputUnderConfiguration: InputConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_setup)
        val inputListAdapter = InputListAdapter(object : OnInputConfigClickedListener {
            override fun onInputConfigClicked(inputConfig: InputConfig) {
                if (inputUnderConfiguration != null)
                    viewModel.stopUpdatingInputConfig(inputUnderConfiguration!!.input)

                viewModel.startUpdatingInputConfig(inputConfig.input)
                waitingForInput = true
                inputUnderConfiguration = inputConfig
            }

            override fun onInputConfigCleared(inputConfig: InputConfig) {
                if (inputUnderConfiguration != null) {
                    if (inputConfig.input === inputUnderConfiguration!!.input)
                        viewModel.stopUpdatingInputConfig(inputConfig.input)

                    inputUnderConfiguration = null
                    waitingForInput = false
                }
                viewModel.clearInput(inputConfig.input)
            }
        })
        inputListAdapter.setHasStableIds(true)

        listInput.apply {
            val listLayoutManager = LinearLayoutManager(context)
            layoutManager = listLayoutManager
            addItemDecoration(DividerItemDecoration(context, listLayoutManager.orientation))
            adapter = inputListAdapter
        }
        viewModel.getInputConfig().observe(this, Observer { statefulInputConfigs -> inputListAdapter.setInputList(statefulInputConfigs) })
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
        inner class InputViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private lateinit var inputConfig: StatefulInputConfig

            fun setInputConfig(config: StatefulInputConfig) {
                inputConfig = config
                val inputNameResource = getInputName(config.inputConfig.input)
                if (inputNameResource == -1)
                    itemView.textInputName.text = config.inputConfig.input.toString()
                else
                    itemView.textInputName.setText(inputNameResource)

                itemView.imageInputClear.visibility = if (config.inputConfig.hasKeyAssigned()) View.VISIBLE else View.GONE

                if (config.isBeingConfigured) {
                    itemView.textAssignedInputName.setText(R.string.press_any_button)
                } else {
                    if (config.inputConfig.hasKeyAssigned()) {
                        val keyCodeString = KeyEvent.keyCodeToString(config.inputConfig.key)
                        val keyName = keyCodeString.replace("KEYCODE", "").replace("_", " ").trim()
                        itemView.textAssignedInputName.text = keyName
                    } else {
                        itemView.textAssignedInputName.setText(R.string.not_set)
                    }
                }
            }

            fun setOnClearInputClickListener(listener: View.OnClickListener) {
                itemView.imageInputClear.setOnClickListener(listener)
            }

            fun getInputConfig(): InputConfig {
                return inputConfig.inputConfig
            }
        }

        private val inputList: ArrayList<StatefulInputConfig> = ArrayList()

        fun setInputList(inputList: List<StatefulInputConfig>) {
            this.inputList.clear()
            this.inputList.addAll(inputList)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InputViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_input, parent, false)
            val viewHolder = InputViewHolder(view)

            view.setOnClickListener { inputConfigClickedListener.onInputConfigClicked(viewHolder.getInputConfig()) }
            viewHolder.setOnClearInputClickListener(View.OnClickListener {
                inputConfigClickedListener.onInputConfigCleared(viewHolder.getInputConfig())
            })

            return viewHolder
        }

        override fun onBindViewHolder(holder: InputViewHolder, position: Int) {
            holder.setInputConfig(inputList[position])
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