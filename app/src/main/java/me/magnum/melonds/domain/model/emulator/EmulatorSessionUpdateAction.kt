package me.magnum.melonds.domain.model.emulator

sealed class EmulatorSessionUpdateAction {
    data object EnableRetroAchievements : EmulatorSessionUpdateAction()
    data object DisableRetroAchievements : EmulatorSessionUpdateAction()
    data object NotifyRetroAchievementsModeSwitch : EmulatorSessionUpdateAction()
}