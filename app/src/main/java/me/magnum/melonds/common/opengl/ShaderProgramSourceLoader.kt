package me.magnum.melonds.common.opengl

import java.io.InputStream

object ShaderProgramSourceLoader {
    class ShaderProgramSourceLoadException(message: String) : Exception(message)

    data class Result(val name: String?, val source: ShaderProgramSource)

    private enum class Section {
        NONE,
        VERTEX,
        FRAGMENT,
    }

    private val KEY_VALUE_REGEX = Regex("^([^:=]+)[:=](.*)")

    fun load(stream: InputStream): Result {
        return stream.bufferedReader().use { reader ->
            load(reader.readText())
        }
    }

    fun load(content: String): Result {
        val vertexBuilder = StringBuilder()
        val fragmentBuilder = StringBuilder()
        var section = Section.NONE
        var textureFiltering: ShaderProgramSource.TextureFiltering? = null
        var name: String? = null

        var attribUv: String? = null
        var attribPos: String? = null
        var attribAlpha: String? = null
        var uniformTex: String? = null
        var uniformPrevTex: String? = null
        var uniformPrevWeight: String? = null
        var uniformTexSize: String? = null
        var uniformViewportSize: String? = null
        var uniformScreenUvBounds: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            when (section) {
                Section.VERTEX -> {
                    val trimmed = line.trim()
                    if (trimmed.equals("[/vertex]", ignoreCase = true)) {
                        section = Section.NONE
                    } else {
                        vertexBuilder.append(line).append('\n')
                    }
                }
                Section.FRAGMENT -> {
                    val trimmed = line.trim()
                    if (trimmed.equals("[/fragment]", ignoreCase = true)) {
                        section = Section.NONE
                    } else {
                        fragmentBuilder.append(line).append('\n')
                    }
                }
                Section.NONE -> {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        return@forEach
                    }
                    when {
                        trimmed.equals("[vertex]", ignoreCase = true) -> section = Section.VERTEX
                        trimmed.equals("[fragment]", ignoreCase = true) -> section = Section.FRAGMENT
                        else -> {
                            val match = KEY_VALUE_REGEX.matchEntire(trimmed)
                            if (match != null) {
                                val key = match.groupValues[1].trim().lowercase()
                                val value = match.groupValues[2].trim()
                                when (key) {
                                    "name" -> name = value.ifBlank { null }
                                    "texturefiltering", "texture_filtering" -> {
                                        textureFiltering = runCatching {
                                            ShaderProgramSource.TextureFiltering.valueOf(value.uppercase())
                                        }.getOrElse {
                                            throw ShaderProgramSourceLoadException("Invalid texture filtering value '$value'")
                                        }
                                    }
                                    "bindings.attribuv", "attribuv" -> attribUv = value
                                    "bindings.attribpos", "attribpos" -> attribPos = value
                                    "bindings.attribalpha", "attribalpha" -> attribAlpha = value
                                    "bindings.uniformtex", "uniformtex" -> uniformTex = value
                                    "bindings.uniformprevtex", "uniformprevtex" -> uniformPrevTex = value
                                    "bindings.uniformprevweight", "uniformprevweight" -> uniformPrevWeight = value
                                    "bindings.uniformtexsize", "uniformtexsize" -> uniformTexSize = value
                                    "bindings.uniformviewportsize", "uniformviewportsize" -> uniformViewportSize = value
                                    "bindings.uniformscreenuvbounds", "uniformscreenuvbounds", "bindings.uniformuvbounds", "uniformuvbounds" -> uniformScreenUvBounds = value
                                    else -> throw ShaderProgramSourceLoadException("Unknown property '$key'")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (section != Section.NONE) {
            throw ShaderProgramSourceLoadException("Shader file ended before closing section")
        }

        if (vertexBuilder.isEmpty()) {
            throw ShaderProgramSourceLoadException("Vertex shader section is missing")
        }
        if (fragmentBuilder.isEmpty()) {
            throw ShaderProgramSourceLoadException("Fragment shader section is missing")
        }

        val filtering = textureFiltering ?: ShaderProgramSource.TextureFiltering.NEAREST
        val defaultBindings = ShaderProgramSource.Bindings()
        val bindings = ShaderProgramSource.Bindings(
            attribUv = attribUv?.ifBlank { defaultBindings.attribUv } ?: defaultBindings.attribUv,
            attribPos = attribPos?.ifBlank { defaultBindings.attribPos } ?: defaultBindings.attribPos,
            attribAlpha = attribAlpha?.ifBlank { defaultBindings.attribAlpha } ?: defaultBindings.attribAlpha,
            uniformTex = uniformTex?.ifBlank { defaultBindings.uniformTex } ?: defaultBindings.uniformTex,
            uniformPrevTex = uniformPrevTex?.ifBlank { defaultBindings.uniformPrevTex } ?: defaultBindings.uniformPrevTex,
            uniformPrevWeight = uniformPrevWeight?.ifBlank { defaultBindings.uniformPrevWeight } ?: defaultBindings.uniformPrevWeight,
            uniformTexSize = uniformTexSize?.ifBlank { defaultBindings.uniformTexSize } ?: defaultBindings.uniformTexSize,
            uniformViewportSize = uniformViewportSize?.ifBlank { defaultBindings.uniformViewportSize } ?: defaultBindings.uniformViewportSize,
            uniformScreenUvBounds = uniformScreenUvBounds?.ifBlank { defaultBindings.uniformScreenUvBounds } ?: defaultBindings.uniformScreenUvBounds,
        )

        val vertexShaderSource = vertexBuilder.toString()
        val fragmentShaderSource = fragmentBuilder.toString()

        val source = ShaderProgramSource.from(filtering, vertexShaderSource, fragmentShaderSource, bindings)
        return Result(name, source)
    }
}
