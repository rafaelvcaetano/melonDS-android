package me.magnum.rcheevosapi

import me.magnum.rcheevosapi.model.RAUserAuth

interface RAAchievementSignatureProvider {
    fun provideAchievementSignature(achievementId: Long, userAuth: RAUserAuth, forHardcoreMode: Boolean): String
}