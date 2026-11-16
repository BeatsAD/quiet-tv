#include <jni.h>
#include <stdlib.h>
extern char *qt_remote(char *raw);
extern void qt_remote_free(char *value);
JNIEXPORT jstring JNICALL Java_home_quiet_tv_TvRemoteNative_call(JNIEnv *env, jclass cls, jstring input) {
    (void)cls;
    const char *raw = (*env)->GetStringUTFChars(env, input, NULL);
    if (raw == NULL) return NULL;
    char *result = qt_remote((char *)raw);
    (*env)->ReleaseStringUTFChars(env, input, raw);
    jstring value = (*env)->NewStringUTF(env, result);
    qt_remote_free(result);
    return value;
}
