package me.magnum.rcheevosapi

import me.magnum.rcheevosapi.model.RAUserAuth

interface RASignatureProvider {
    fun provideAchievementSignature(achievementId: Long, userAuth: RAUserAuth, forHardcoreMode: Boolean): String
    fun provideLeaderboardSignature(leaderboardId: Long, score: Int, userAuth: RAUserAuth): String
}