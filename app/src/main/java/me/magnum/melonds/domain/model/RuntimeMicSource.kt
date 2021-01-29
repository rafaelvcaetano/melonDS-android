package me.magnum.melonds.domain.model

enum class RuntimeMicSource(val micSource: MicSource?) : RuntimeEnum<RuntimeMicSource, MicSource> {
    DEFAULT(null),
    NONE(MicSource.NONE),
    BLOW(MicSource.BLOW),
    DEVICE(MicSource.DEVICE);

    override fun getDefault() = DEFAULT
    override fun getValue() = micSource!!
}
