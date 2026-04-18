#ifndef RETROACHIEVEMENTSMAPPER_H
#define RETROACHIEVEMENTSMAPPER_H

#include <jni.h>
#include <list>
#include "retroachievements/RAAchievement.h"
#include "retroachievements/RALeaderboard.h"

void mapAchievementsFromJava(JNIEnv *env, jobjectArray javaAchievements, std::list<MelonDSAndroid::RetroAchievements::RAAchievement> &outputList);
void mapLeaderboardsFromJava(JNIEnv *env, jobjectArray javaLeaderboards, std::list<MelonDSAndroid::RetroAchievements::RALeaderboard> &outputList);

#endif //RETROACHIEVEMENTSMAPPER_H
