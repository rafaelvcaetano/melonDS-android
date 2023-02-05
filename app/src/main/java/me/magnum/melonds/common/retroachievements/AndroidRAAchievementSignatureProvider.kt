package me.magnum.melonds.common.retroachievements

import me.magnum.rcheevosapi.RAAchievementSignatureProvider
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAUserAuth

class AndroidRAAchievementSignatureProvider : RAAchievementSignatureProvider {

    override fun provideAchievementSignature(achievement: RAAchievement, userAuth: RAUserAuth, forHardcoreMode: Boolean): String {
        return ""
    }
}