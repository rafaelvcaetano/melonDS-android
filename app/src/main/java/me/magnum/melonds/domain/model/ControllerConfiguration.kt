package me.magnum.melonds.domain.model

class ControllerConfiguration(configList: List<InputConfig>) {
    companion object {
        private val configurableInput = listOf(
            Input.A,
            Input.B,
            Input.X,
            Input.Y,
            Input.LEFT,
            Input.RIGHT,
            Input.UP,
            Input.DOWN,
            Input.L,
            Input.R,
            Input.START,
            Input.SELECT,
            Input.HINGE,
            Input.PAUSE,
            Input.FAST_FORWARD,
            Input.MICROPHONE,
            Input.RESET,
            Input.SWAP_SCREENS,
            Input.QUICK_SAVE,
            Input.QUICK_LOAD,
            Input.REWIND,
            Input.REFRESH_EXTERNAL_SCREEN
        )
    }

    val inputMapper: List<InputConfig>

    init {
        inputMapper = configurableInput.map { input ->
            // Find config in the config list, or fallback to a default config without assignments
            configList.firstOrNull { it.input == input } ?: InputConfig(input)
        }
    }

    fun getInputAssignments(input: Input): List<InputConfig.Assignment>? {
        return inputMapper.firstOrNull { it.input == input && it.hasKeyAssigned() }?.let {
            listOfNotNull(
                it.assignment.takeIf { it != InputConfig.Assignment.None },
                it.altAssignment.takeIf { it != InputConfig.Assignment.None },
            )
        }
    }

    fun keyToInput(key: Int): Input? {
        for (config in inputMapper) {
            val assignments = listOf(config.assignment, config.altAssignment)
            if (assignments.any { (it as? InputConfig.Assignment.Key)?.keyCode == key }) {
                return config.input
            }
        }
        return null
    }

    fun axisToInput(axis: Int, direction: InputConfig.Assignment.Axis.Direction): Input? {
        return inputMapper.firstOrNull { config ->
            val assignments = listOf(config.assignment, config.altAssignment)
            assignments.any {
                (it as? InputConfig.Assignment.Axis)?.let { ax ->
                    ax.axisCode == axis && ax.direction == direction
                } ?: false
            }
        }?.input
    }
}