#ifndef RAACHIEVEMENTMAPPER_H
#define RAACHIEVEMENTMAPPER_H

#include <jni.h>
#include <list>
#include "retroachievements/RAAchievement.h"

void mapAchievementsFromJava(JNIEnv *env, jobjectArray javaAchievements, std::list<MelonDSAndroid::RetroAchievements::RAAchievement> &outputList);

#endif //RAACHIEVEMENTMAPPER_H
