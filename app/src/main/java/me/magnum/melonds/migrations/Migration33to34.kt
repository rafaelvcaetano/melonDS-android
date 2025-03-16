package me.magnum.melonds.migrations

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.InputConfig
import me.magnum.melonds.impl.dtos.input.ControllerConfigurationDto
import me.magnum.melonds.migrations.legacy.input.ControllerConfigurationDto33
import java.io.File

class Migration33to34(
    private val context: Context,
    private val json: Json,
) : Migration {

    private companion object {
        const val CONTROLLER_CONFIG_FILE = "controller_config.json"
        const val KEY_NOT_SET = -1
    }

    override val from = 33
    override val to = 34

    @OptIn(ExperimentalSerializationApi::class)
    override fun migrate() {
        val controllerConfigFile = File(context.filesDir, CONTROLLER_CONFIG_FILE)
        try {
            val originalData = controllerConfigFile.inputStream().use {
                json.decodeFromStream<ControllerConfigurationDto33>(it)
            }

            val newData = ControllerConfiguration(
                configList = originalData.inputMapper.map {
                    val assignment = if (it.key == KEY_NOT_SET) {
                        InputConfig.Assignment.None
                    } else {
                        InputConfig.Assignment.Key(null, it.key)
                    }
                    InputConfig(it.input, assignment)
                }
            )
            val newDto = ControllerConfigurationDto.fromControllerConfiguration(newData)
            controllerConfigFile.outputStream().use {
                json.encodeToStream(newDto, it)
            }
        } catch (e: Exception) {
            controllerConfigFile.delete()
        }
    }
}