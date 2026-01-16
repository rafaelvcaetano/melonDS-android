#include "RetroAchievementsMapper.h"

void mapAchievementsFromJava(JNIEnv *env, jobjectArray javaAchievements, std::list<MelonDSAndroid::RetroAchievements::RAAchievement> &outputList)
{
    jsize achievementCount = env->GetArrayLength(javaAchievements);
    if (achievementCount < 1)
        return;

    jclass cheatClass = env->GetObjectClass(env->GetObjectArrayElement(javaAchievements, 0));
    jfieldID idField = env->GetFieldID(cheatClass, "id", "J");
    jfieldID memoryAddressField = env->GetFieldID(cheatClass, "memoryAddress", "Ljava/lang/String;");

    for (int i = 0; i < achievementCount; ++i)
    {
        jobject achievement = env->GetObjectArrayElement(javaAchievements, i);
        jlong id = env->GetLongField(achievement, idField);
        jstring memoryAddress = (jstring) env->GetObjectField(achievement, memoryAddressField);
        jboolean isStringCopy;
        const char* codeString = env->GetStringUTFChars(memoryAddress, &isStringCopy);

        MelonDSAndroid::RetroAchievements::RAAchievement internalAchievement = {
            .id = (long) id,
            .memoryAddress = std::string(codeString),
        };

        if (isStringCopy)
            env->ReleaseStringUTFChars(memoryAddress, codeString);

        outputList.push_back(internalAchievement);
    }
}

void mapLeaderboardsFromJava(JNIEnv *env, jobjectArray javaLeaderboards, std::list<MelonDSAndroid::RetroAchievements::RALeaderboard> &outputList)
{
    jsize leaderboardsCount = env->GetArrayLength(javaLeaderboards);
    if (leaderboardsCount < 1)
        return;

    jclass cheatClass = env->GetObjectClass(env->GetObjectArrayElement(javaLeaderboards, 0));
    jfieldID idField = env->GetFieldID(cheatClass, "id", "J");
    jfieldID memoryAddressField = env->GetFieldID(cheatClass, "memoryAddress", "Ljava/lang/String;");
    jfieldID formatField = env->GetFieldID(cheatClass, "format", "Ljava/lang/String;");

    for (int i = 0; i < leaderboardsCount; ++i)
    {
        jobject leaderboard = env->GetObjectArrayElement(javaLeaderboards, i);
        jlong id = env->GetLongField(leaderboard, idField);
        jstring memoryAddress = (jstring) env->GetObjectField(leaderboard, memoryAddressField);
        jstring format = (jstring) env->GetObjectField(leaderboard, formatField);
        const char* codeString = env->GetStringUTFChars(memoryAddress, nullptr);
        const char* formatString = env->GetStringUTFChars(format, nullptr);

        MelonDSAndroid::RetroAchievements::RALeaderboard internalLeaderboard = {
            .id = (long) id,
            .memoryAddress = codeString,
            .format = formatString,
        };

        env->ReleaseStringUTFChars(memoryAddress, codeString);
        env->ReleaseStringUTFChars(format, formatString);

        outputList.push_back(internalLeaderboard);
    }
}