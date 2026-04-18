#include <jni.h>
#include <EGL/egl.h>

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

}