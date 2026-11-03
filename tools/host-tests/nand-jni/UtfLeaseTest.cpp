#include <iostream>
#include <stdexcept>
#include "jni.h"
#include "TestDependencies.h"

struct LeaseJNIEnv : JNIEnv {
    int acquired = 0;
    int released = 0;
    bool failAcquisition = false;

    const char* GetStringUTFChars(jstring string, jboolean* copy) {
        if (failAcquisition) return nullptr;
        ++acquired;
        return JNIEnv::GetStringUTFChars(string, copy);
    }

    void ReleaseStringUTFChars(jstring string, const char* chars) {
        ++released;
        JNIEnv::ReleaseStringUTFChars(string, chars);
    }
};

// Keep the production JNI functions while recording their string leases.
#define JNIEnv LeaseJNIEnv
#include "MelonDSNandJNI.cpp"
#undef JNIEnv

static void check(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}

int main() {
    try {
        u8 key[16]{};
        melonDS::DSi_NAND::NANDImage image(nullptr, key);
        melonDS::DSi_NAND::NANDMount mount(image);
        nandMount = &mount;

        LeaseJNIEnv env;
        auto uri = env.NewStringUTF("content://audit/save");
        check(Java_me_magnum_melonds_MelonDSiNand_importTitleFile(
                  &env, nullptr, 0x54455354, 0, uri), "import boundary failed");
        check(Java_me_magnum_melonds_MelonDSiNand_exportTitleFile(
                  &env, nullptr, 0x54455354, 0, uri), "export boundary failed");
        check(env.acquired == 2, "both URI strings must be acquired");
        check(env.released == env.acquired,
              "every JNI UTF lease must be released when isCopy is false");
        env.failAcquisition = true;
        check(!Java_me_magnum_melonds_MelonDSiNand_importTitleFile(
                  &env, nullptr, 0x54455354, 0, uri),
              "failed import URI acquisition must return failure");
        check(!Java_me_magnum_melonds_MelonDSiNand_exportTitleFile(
                  &env, nullptr, 0x54455354, 0, uri),
              "failed export URI acquisition must return failure");
        check(env.acquired == 2 && env.released == 2,
              "failed acquisitions must not create or release a lease");
        env.DeleteLocalRef(uri);
        std::cout << "PASS: both JNI UTF leases released once\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "FAIL: " << error.what() << '\n';
        return 1;
    }
}
