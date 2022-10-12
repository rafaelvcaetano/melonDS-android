package me.magnum.melonds.domain.model

data class Version(val type: ReleaseType, val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    enum class ReleaseType {
        ALPHA,
        BETA,
        FINAL
    }

    companion object {
        /**
         * Converts a version string in the format of `[alpha|beta-]major.minor.patch` to a [Version]. If a string with an invalid format is provided, an exception will be
         * thrown.
         */
        fun fromString(versionString: String): Version {
            val parts = versionString.split('-')
            return if (parts.size == 1) {
                val intParts = ensureMinimumVersionParts(parts[0].split('.').map { it.toInt() })
                Version(ReleaseType.FINAL, intParts[0], intParts[1], intParts[2])
            } else if (parts.size == 2) {
                val versionType = releaseTypeStringToValue(parts[0])
                val intParts = ensureMinimumVersionParts(parts[1].split('.').map { it.toInt() })
                Version(versionType, intParts[0], intParts[1], intParts[2])
            } else {
                throw Exception("Invalid version string format")
            }
        }

        private fun releaseTypeStringToValue(typeString: String): ReleaseType {
            val upperCaseType = typeString.uppercase()
            return ReleaseType.valueOf(upperCaseType)
        }

        /**
         * Ensures that the given list of version parts contains the minimum size (3). If the list has less than 3 items, 0s are added until there are 3 parts.
         */
        private fun ensureMinimumVersionParts(parts: List<Int>): List<Int> {
            return if (parts.size == 3) {
                parts
            } else {
                val remaining = 3 - parts.size
                val newList = mutableListOf<Int>()
                newList.addAll(parts)
                for (i in 0..remaining) {
                    newList.add(0)
                }

                newList
            }
        }
    }

    private fun parts(): IntArray {
        return intArrayOf(
            type.ordinal,
            major,
            minor,
            patch
        )
    }

    override fun compareTo(other: Version): Int {
        val thisParts = parts()
        val otherParts = other.parts()

        for (i in 0 until 4) {
            val diff = thisParts[i] - otherParts[i]
            if (diff != 0) {
                return diff
            }
        }
        return 0
    }

    override fun toString(): String {
        val typeString = when(type) {
            ReleaseType.ALPHA -> "alpha"
            ReleaseType.BETA -> "beta"
            ReleaseType.FINAL -> ""
        }
        return "$typeString${if (typeString.isEmpty()) "" else "-"}$major.$minor.$patch"
    }
}