package me.magnum.melonds.common.retroachievements

import me.magnum.rcheevosapi.RAAchievementSignatureProvider
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAUserAuth
import java.math.BigInteger
import java.security.MessageDigest

class AndroidRAAchievementSignatureProvider : RAAchievementSignatureProvider {

    override fun provideAchievementSignature(achievement: RAAchievement, userAuth: RAUserAuth, forHardcoreMode: Boolean): String {
        val md5Digest = MessageDigest.getInstance("MD5")
        md5Digest.update(achievement.id.toString().toByteArray())
        md5Digest.update(userAuth.username.toByteArray())
        md5Digest.update((if (forHardcoreMode) "1" else "0").toByteArray())

        return BigInteger(1, md5Digest.digest()).toString(16).padStart(32, '0')
    }
}