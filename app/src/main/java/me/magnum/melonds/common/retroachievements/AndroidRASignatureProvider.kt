package me.magnum.melonds.common.retroachievements

import me.magnum.rcheevosapi.RASignatureProvider
import me.magnum.rcheevosapi.model.RAUserAuth
import java.math.BigInteger
import java.security.MessageDigest

class AndroidRASignatureProvider : RASignatureProvider {

    override fun provideAchievementSignature(achievementId: Long, userAuth: RAUserAuth, forHardcoreMode: Boolean): String {
        val md5Digest = MessageDigest.getInstance("MD5")
        md5Digest.update(achievementId.toString().toByteArray())
        md5Digest.update(userAuth.username.toByteArray())
        md5Digest.update((if (forHardcoreMode) "1" else "0").toByteArray())

        return BigInteger(1, md5Digest.digest()).toString(16).padStart(32, '0')
    }

    override fun provideLeaderboardSignature(leaderboardId: Long, score: Int, userAuth: RAUserAuth): String {
        val md5Digest = MessageDigest.getInstance("MD5")
        md5Digest.update(leaderboardId.toString().toByteArray())
        md5Digest.update(score.toString().toByteArray())
        md5Digest.update(userAuth.username.toByteArray())

        return BigInteger(1, md5Digest.digest()).toString(16).padStart(32, '0')
    }
}