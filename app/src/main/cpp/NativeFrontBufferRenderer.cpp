#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>

// Define the types we need from GL_OES_EGL_image without pulling in gl2ext.h
typedef void* GLeglImageOES;
typedef void (*PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)(GLenum target, GLeglImageOES image);
typedef void (*PFNGLEGLIMAGETARGETRENDERBUFFERSTORAGEOESPROC)(GLenum target, GLeglImageOES image);

extern "C"
{

JNIEXPORT void JNICALL
Java_me_magnum_melonds_ui_emulator_render_SurfaceFrontRenderer_nativeGlEGLImageTargetTexture2DOES(JNIEnv* env, jobject thiz, jlong eglImage)
{
    auto image = reinterpret_cast<GLeglImageOES>(eglImage);

    auto fn = (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC) eglGetProcAddress("glEGLImageTargetTexture2DOES");

    if (fn != nullptr) {
        fn(GL_TEXTURE_2D, image);
    }
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_ui_emulator_render_SurfaceFrontRenderer_nativeGlEGLImageTargetRenderbufferStorageOES(JNIEnv* env, jobject thiz, jlong eglImage)
{
    auto image = reinterpret_cast<GLeglImageOES>(eglImage);

    auto fn = (PFNGLEGLIMAGETARGETRENDERBUFFERSTORAGEOESPROC) eglGetProcAddress("glEGLImageTargetRenderbufferStorageOES");

    if (fn != nullptr) {
        fn(GL_RENDERBUFFER, image);
    }
}

}
