#include <jni.h>
#include <dlfcn.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/hardware_buffer.h>

#define EGL_NATIVE_BUFFER_ANDROID 0x3140

// AHardwareBuffer_fromHardwareBuffer is not available at lower API levels. Load it dynamically
typedef AHardwareBuffer* (*PFN_AHardwareBuffer_fromHardwareBuffer)(JNIEnv* env, jobject hardwareBufferObj);

static PFN_AHardwareBuffer_fromHardwareBuffer getAHardwareBufferFromHardwareBuffer()
{
    static PFN_AHardwareBuffer_fromHardwareBuffer function = nullptr;
    static bool resolved = false;

    if (!resolved) {
        void* lib = dlopen("libandroid.so", RTLD_LAZY);
        if (lib != nullptr) {
            function = (PFN_AHardwareBuffer_fromHardwareBuffer) dlsym(lib, "AHardwareBuffer_fromHardwareBuffer");
        }
        resolved = true;
    }

    return function;
}

extern "C"
{

JNIEXPORT jlong JNICALL
Java_me_magnum_melonds_ui_emulator_render_GlContext_createContext(JNIEnv* env, jobject thiz, jlong display, jlong config, jlong sharedGlContext)
{
    auto eglDisplay = reinterpret_cast<EGLDisplay>(display);
    auto eglConfig = reinterpret_cast<EGLConfig>(config);
    auto eglSharedContext = reinterpret_cast<EGLContext>(sharedGlContext);

    int contextAttributes[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE
    };
    return (jlong) eglCreateContext(eglDisplay, eglConfig, eglSharedContext, contextAttributes);
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_ui_emulator_render_GlContext_makeCurrent(JNIEnv* env, jobject thiz, jlong display, jlong surface, jlong context)
{
    auto eglDisplay = reinterpret_cast<EGLDisplay>(display);
    auto eglSurface = reinterpret_cast<EGLSurface>(surface);
    auto eglContext = reinterpret_cast<EGLContext>(context);

    return (jboolean) eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_ui_emulator_render_GlContext_destroyContext(JNIEnv* env, jobject thiz, jlong display, jlong context)
{
    auto eglDisplay = reinterpret_cast<EGLDisplay>(display);
    auto eglContext = reinterpret_cast<EGLContext>(context);

    eglDestroyContext(eglDisplay, eglContext);
}

JNIEXPORT jlong JNICALL
Java_me_magnum_melonds_ui_emulator_render_GlContext_nativeCreateEglImageFromHardwareBuffer(JNIEnv* env, jobject thiz, jlong display, jobject hardwareBuffer)
{
    auto eglDisplay = reinterpret_cast<EGLDisplay>(display);

    auto fromHardwareBufferFunction = getAHardwareBufferFromHardwareBuffer();
    if (fromHardwareBufferFunction == nullptr)
        return 0;

    AHardwareBuffer* nativeBuffer = fromHardwareBufferFunction(env, hardwareBuffer);
    if (nativeBuffer == nullptr)
        return 0;

    auto eglGetNativeClientBufferANDROID = (PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC) eglGetProcAddress("eglGetNativeClientBufferANDROID");
    if (eglGetNativeClientBufferANDROID == nullptr)
        return 0;

    EGLClientBuffer clientBuffer = eglGetNativeClientBufferANDROID(nativeBuffer);
    if (clientBuffer == nullptr)
        return 0;

    auto eglCreateImageKHR = (PFNEGLCREATEIMAGEKHRPROC) eglGetProcAddress("eglCreateImageKHR");
    if (eglCreateImageKHR == nullptr)
        return 0;

    EGLint attrs[] = { EGL_NONE };
    EGLImageKHR image = eglCreateImageKHR(eglDisplay, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuffer, attrs);

    return reinterpret_cast<jlong>(image);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_ui_emulator_render_GlContext_nativeDestroyEglImage(JNIEnv* env, jobject thiz, jlong display, jlong eglImage)
{
    auto eglDisplay = reinterpret_cast<EGLDisplay>(display);
    auto image = reinterpret_cast<EGLImageKHR>(eglImage);

    auto eglDestroyImageKHR = (PFNEGLDESTROYIMAGEKHRPROC) eglGetProcAddress("eglDestroyImageKHR");
    if (eglDestroyImageKHR != nullptr)
        eglDestroyImageKHR(eglDisplay, image);
}
}