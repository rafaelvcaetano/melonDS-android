package me.magnum.melonds.domain.model

enum class AudioInterpolation(val interpolationValue: Int) {
    NONE(0),
    LINEAR(1),
    COSINE(2),
    CUBIC(3)
}