#include <pthread.h>
#include "JniEnvHandler.h"

typedef struct {
    JavaVM* vm;
} ThreadJniContext;

void DeferThreadDetach(JavaVM* vm) {
    static pthread_key_t thread_key;

    // Set up a Thread Specific Data key, and a callback that
    // will be executed when a thread is destroyed.
    // This is only done once, across all threads, and the value
    // associated with the key for any given thread will initially
    // be NULL.
    static auto runOnce = [] {
        const auto err = pthread_key_create(&thread_key, [] (void *threadSpecificContext) {
            if (threadSpecificContext) {
                ThreadJniContext* threadJniContext = (ThreadJniContext*) threadSpecificContext;
                threadJniContext->vm->DetachCurrentThread();
                delete threadJniContext;
            }
        });
        if (err) {
            // Failed to create TSD key
        }
        return 0;
    }();

    // For the callback to actually be executed when a thread exits
    // we need to associate a non-NULL value with the key on that thread.
    const auto threadContext = pthread_getspecific(thread_key);
    if (!threadContext) {
        ThreadJniContext* threadJniContext = new ThreadJniContext {
                .vm = vm
        };
        if (pthread_setspecific(thread_key, threadJniContext)) {
            // Failed to set thread-specific value for key
        }
    }
}

JniEnvHandler::JniEnvHandler(JavaVM* vm) {
    this->vm = vm;
}

JNIEnv* JniEnvHandler::getCurrentThreadEnv()
{
    JNIEnv* env = nullptr;
    // Check if the current thread is attached to the VM
    auto getEnvResult = this->vm->GetEnv((void**) &env, JNI_VERSION_1_6);
    if (getEnvResult == JNI_EDETACHED) {
        if (vm->AttachCurrentThread(&env, NULL) == JNI_OK) {
            DeferThreadDetach(this->vm);
        }
    } else if (getEnvResult == JNI_EVERSION) {
        // Unsupported JNI version
    }
    return env;
}
