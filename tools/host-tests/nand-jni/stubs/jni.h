#pragma once
#include <algorithm>
#include <cassert>
#include <cstdarg>
#include <cstdint>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>
#define JNIEXPORT
#define JNICALL
using jint = int;
using jlong = long long;
using jbyte = signed char;
using jboolean = unsigned char;
using jchar = std::uint16_t;
using jsize = int;
struct JavaObject {
    enum Kind { Class, List, Title, String, Bytes } kind;
    std::string text;
    std::vector<jbyte> bytes;
    std::vector<JavaObject*> children;
    jlong titleId = 0;
};
using jobject = JavaObject*;
using jclass = jobject;
using jstring = jobject;
using jbyteArray = jobject;
using jmethodID = int;

// A small JNI object/ref model, not a pattern-matching test. Objects remain reachable through
// the returned ArrayList after their temporary local handles have been released.
struct JNIEnv {
    std::vector<std::unique_ptr<JavaObject>> objects;
    std::unordered_map<jobject, int> locals;
    int peakLocals = 0;
    int liveLocals = 0;
    std::vector<std::vector<jobject>> localFrames;

    jobject allocate(JavaObject::Kind kind) {
        auto object = std::make_unique<JavaObject>();
        object->kind = kind;
        jobject result = object.get();
        objects.push_back(std::move(object));
        ++locals[result];
        peakLocals = std::max(peakLocals, ++liveLocals);
        if (!localFrames.empty()) localFrames.back().push_back(result);
        return result;
    }
    void requireLocal(jobject object) { assert(object && locals[object] > 0); }
    jclass FindClass(const char* name) {
        auto result = allocate(JavaObject::Class);
        if (result) result->text = name;
        return result;
    }
    jmethodID GetMethodID(jclass cls, const char* name, const char*) {
        requireLocal(cls);
        return std::string(name) == "add" ? 2 : 1;
    }
    jobject NewObject(jclass cls, jmethodID method, ...) {
        requireLocal(cls);
        const bool list = cls->text == "java/util/ArrayList";
        auto result = allocate(list ? JavaObject::List : JavaObject::Title);
        if (!result || list) return result;
        va_list args;
        va_start(args, method);
        jobject title = va_arg(args, jobject);
        jobject producer = va_arg(args, jobject);
        result->titleId = va_arg(args, jlong);
        jobject icon = va_arg(args, jobject);
        requireLocal(title); requireLocal(producer); requireLocal(icon);
        result->children = {title, producer, icon};
        va_end(args);
        return result;
    }
    void CallVoidMethod(jobject list, jmethodID, jint index, jobject value) {
        requireLocal(list); requireLocal(value);
        assert(list->kind == JavaObject::List && value->kind == JavaObject::Title);
        assert(index == static_cast<jint>(list->children.size()));
        list->children.push_back(value);
    }
    jboolean CallBooleanMethod(jobject list, jmethodID, jobject value) {
        requireLocal(list); requireLocal(value);
        assert(list->kind == JavaObject::List && value->kind == JavaObject::Title);
        list->children.push_back(value);
        return 1;
    }
    jint PushLocalFrame(jint) {
        localFrames.emplace_back();
        return 0;
    }
    jobject PopLocalFrame(jobject result) {
        assert(!localFrames.empty());
        auto frame = std::move(localFrames.back());
        localFrames.pop_back();
        for (jobject object : frame) {
            if (locals[object] > 0) {
                --locals[object];
                --liveLocals;
            }
        }
        return result;
    }
    jbyteArray NewByteArray(jint size) {
        auto result = allocate(JavaObject::Bytes);
        if (result) result->bytes.resize(size);
        return result;
    }
    jbyte* GetByteArrayElements(jbyteArray array, jboolean*) {
        requireLocal(array); return array->bytes.data();
    }
    jint GetArrayLength(jbyteArray array) {
        requireLocal(array); return static_cast<jint>(array->bytes.size());
    }
    void GetByteArrayRegion(jbyteArray array, jint start, jint length, jbyte* destination) {
        requireLocal(array);
        assert(start >= 0 && length >= 0 && start + length <= GetArrayLength(array));
        std::copy_n(array->bytes.data() + start, length, destination);
    }
    void ReleaseByteArrayElements(jbyteArray array, jbyte*, jint) { requireLocal(array); }
    jstring NewStringUTF(const char* value) {
        auto result = allocate(JavaObject::String);
        if (result) result->text = value;
        return result;
    }
    jstring NewString(const jchar* value, jsize length) {
        auto result = allocate(JavaObject::String);
        if (!result || !value || length < 0) return result;
        for (jsize index = 0; index < length; ++index) {
            const std::uint32_t code = value[index];
            if (code < 0x80) result->text.push_back(static_cast<char>(code));
            else if (code < 0x800) {
                result->text.push_back(static_cast<char>(0xC0 | (code >> 6)));
                result->text.push_back(static_cast<char>(0x80 | (code & 0x3F)));
            } else {
                const std::uint32_t safe = (code >= 0xD800 && code <= 0xDFFF) ? 0xFFFD : code;
                result->text.push_back(static_cast<char>(0xE0 | (safe >> 12)));
                result->text.push_back(static_cast<char>(0x80 | ((safe >> 6) & 0x3F)));
                result->text.push_back(static_cast<char>(0x80 | (safe & 0x3F)));
            }
        }
        return result;
    }
    const char* GetStringUTFChars(jstring string, jboolean* copy) {
        requireLocal(string); if (copy) *copy = false; return string->text.c_str();
    }
    void ReleaseStringUTFChars(jstring string, const char*) { requireLocal(string); }
    void DeleteLocalRef(jobject object) {
        if (!object) return;
        requireLocal(object); --locals[object]; --liveLocals;
    }
    jboolean ExceptionCheck() { return false; }
};
