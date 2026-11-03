#include "RetroAchievementsMapper.h"

namespace
{
constexpr jsize MaxAchievementCount = 10000;
constexpr jsize MaxLeaderboardCount = 5000;
constexpr jsize MaxExpressionBytes = 64 * 1024;
}

void mapAchievementsFromJava(JNIEnv *env, jobjectArray javaAchievements, std::list<MelonDSAndroid::RetroAchievements::RAAchievement> &outputList)
{
    jsize achievementCount = env->GetArrayLength(javaAchievements);
    if (achievementCount < 1 || achievementCount > MaxAchievementCount)
        return;

    jobject first = env->GetObjectArrayElement(javaAchievements, 0);
    jclass cheatClass = env->GetObjectClass(first);
    env->DeleteLocalRef(first);
    jfieldID idField = env->GetFieldID(cheatClass, "id", "J");
    jfieldID memoryAddressField = env->GetFieldID(cheatClass, "memoryAddress", "Ljava/lang/String;");

    for (int i = 0; i < achievementCount; ++i)
    {
        if (env->PushLocalFrame(8) < 0) break;
        jobject achievement = env->GetObjectArrayElement(javaAchievements, i);
        jlong id = env->GetLongField(achievement, idField);
        jstring memoryAddress = (jstring) env->GetObjectField(achievement, memoryAddressField);
        if (!memoryAddress || env->GetStringUTFLength(memoryAddress) > MaxExpressionBytes)
        {
            env->PopLocalFrame(nullptr);
            outputList.clear();
            break;
        }
        const char* codeString = env->GetStringUTFChars(memoryAddress, nullptr);
        if (!codeString)
        {
            env->PopLocalFrame(nullptr);
            outputList.clear();
            break;
        }

        MelonDSAndroid::RetroAchievements::RAAchievement internalAchievement = {
            .id = (long) id,
            .memoryAddress = std::string(codeString),
        };

        env->ReleaseStringUTFChars(memoryAddress, codeString);

        outputList.push_back(internalAchievement);
        env->PopLocalFrame(nullptr);
    }
    env->DeleteLocalRef(cheatClass);
}

void mapLeaderboardsFromJava(JNIEnv *env, jobjectArray javaLeaderboards, std::list<MelonDSAndroid::RetroAchievements::RALeaderboard> &outputList)
{
    jsize leaderboardsCount = env->GetArrayLength(javaLeaderboards);
    if (leaderboardsCount < 1 || leaderboardsCount > MaxLeaderboardCount)
        return;

    jobject first = env->GetObjectArrayElement(javaLeaderboards, 0);
    jclass cheatClass = env->GetObjectClass(first);
    env->DeleteLocalRef(first);
    jfieldID idField = env->GetFieldID(cheatClass, "id", "J");
    jfieldID memoryAddressField = env->GetFieldID(cheatClass, "memoryAddress", "Ljava/lang/String;");
    jfieldID formatField = env->GetFieldID(cheatClass, "format", "Ljava/lang/String;");

    for (int i = 0; i < leaderboardsCount; ++i)
    {
        if (env->PushLocalFrame(10) < 0) break;
        jobject leaderboard = env->GetObjectArrayElement(javaLeaderboards, i);
        jlong id = env->GetLongField(leaderboard, idField);
        jstring memoryAddress = (jstring) env->GetObjectField(leaderboard, memoryAddressField);
        jstring format = (jstring) env->GetObjectField(leaderboard, formatField);
        if (!memoryAddress || !format || env->GetStringUTFLength(memoryAddress) > MaxExpressionBytes ||
            env->GetStringUTFLength(format) > MaxExpressionBytes)
        {
            env->PopLocalFrame(nullptr);
            outputList.clear();
            break;
        }
        const char* codeString = env->GetStringUTFChars(memoryAddress, nullptr);
        const char* formatString = env->GetStringUTFChars(format, nullptr);
        if (!codeString || !formatString)
        {
            if (codeString) env->ReleaseStringUTFChars(memoryAddress, codeString);
            if (formatString) env->ReleaseStringUTFChars(format, formatString);
            env->PopLocalFrame(nullptr);
            outputList.clear();
            break;
        }

        MelonDSAndroid::RetroAchievements::RALeaderboard internalLeaderboard = {
            .id = (long) id,
            .memoryAddress = codeString,
            .format = formatString,
        };

        env->ReleaseStringUTFChars(memoryAddress, codeString);
        env->ReleaseStringUTFChars(format, formatString);

        outputList.push_back(internalLeaderboard);
        env->PopLocalFrame(nullptr);
    }
    env->DeleteLocalRef(cheatClass);
}
