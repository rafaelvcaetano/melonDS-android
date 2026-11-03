package me.magnum.melonds.impl

import java.security.MessageDigest

internal object RomStorageName {
    private const val MAX_STEM_CHARS = 96

    fun stem(displayName: String?, identity: String, collisionSafe: Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.encodeToByteArray())
            .take(10)
            .joinToString("") { "%02x".format(it) }

        val leaf = displayName
        ?.takeIf { it.isNotBlank() && it != "." && it != ".." && '/' !in it && '\\' !in it && '\u0000' !in it }
        val rawStem = leaf?.substringBeforeLast('.', leaf)?.takeIf { it.isNotBlank() }
        val safeStem = rawStem?.take(MAX_STEM_CHARS) ?: "rom"
        return if (collisionSafe || leaf == null) "$safeStem-$digest" else safeStem
    }

    fun compatibleStems(
        displayName: String?,
        identity: String,
        collisionSafe: Boolean,
        allowLegacyFallback: Boolean,
    ): List<String> {
        val primary = stem(displayName, identity, collisionSafe)
        if (!collisionSafe || !allowLegacyFallback) return listOf(primary)
        val legacy = stem(displayName, identity, collisionSafe = false)
        return if (legacy == primary || legacy.startsWith("rom-")) listOf(primary) else listOf(primary, legacy)
    }
}
