package me.magnum.rcheevosapi

import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAUserAuth

interface RAAchievementSignatureProvider {
    fun provideAchievementSignature(achievement: RAAchievement, userAuth: RAUserAuth, forHardcoreMode: Boolean): String
}