#include "RAAchievementMapper.h"

void mapAchievementsFromJava(JNIEnv *env, jobjectArray javaAchievements, std::list<RetroAchievements::RAAchievement> &outputList)
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

        RetroAchievements::RAAchievement internalAchievement = {
                .id = (long) id,
                .memoryAddress = std::string(codeString),
        };

        if (isStringCopy)
            env->ReleaseStringUTFChars(memoryAddress, codeString);

        outputList.push_back(internalAchievement);
    }
}