/*
 * Native tree loader for downloadable extension modules
 * (spec-ffmpeg-extension-module - spike-verified, round 3d).
 *
 * Dlopens every .so by full path, in DT_NEEDED topological order, from this
 * helper's own namespace. A plain dlopen is attributed to its caller: this
 * helper is System.loadLibrary'd from app code, so its namespace is the
 * classloader namespace (clns-N) where the module's Java library runs its own
 * System.loadLibrary on static init. Once every DT_NEEDED of a library is
 * already loaded in that namespace, bionic's already-loaded check returns the
 * existing soinfos without consulting any search path, and the library's own
 * loadLibrary calls become no-ops.
 *
 * Only plain dlopen/dlerror are used - no LD_LIBRARY_PATH manipulation, no
 * android_dlopen_ext, no private linker symbols.
 */
#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>

JNIEXPORT jstring JNICALL
Java_app_n_1zik_android_modules_ModuleNativeLoader_nativeLoadTree(
        JNIEnv *env, jclass, jstring j_dir, jstring j_order) {
    char report[12288];
    int pos = 0;

    const char* dir = (*env)->GetStringUTFChars(env, j_dir, NULL);
    const char* order = (*env)->GetStringUTFChars(env, j_order, NULL);
    if (dir == NULL || order == NULL) {
        jclass oome = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        (*env)->ThrowNew(env, oome, NULL);
        if (dir != NULL) {
            (*env)->ReleaseStringUTFChars(env, j_dir, dir);
        }
        if (order != NULL) {
            (*env)->ReleaseStringUTFChars(env, j_order, order);
        }
        return NULL;
    }

    /* Mutable copy - split on ':' in place (NDK r30 deprecated strtok). */
    char order_copy[4096];
    strncpy(order_copy, order, sizeof(order_copy) - 1);
    order_copy[sizeof(order_copy) - 1] = '\0';

    for (char* cursor = order_copy; cursor[0] != '\0';) {
        char* name = cursor;
        char* colon = strchr(name, ':');
        if (colon != NULL) {
            *colon = '\0';
            cursor = colon + 1;
        } else {
            cursor += strlen(name);
        }
        if (name[0] == '\0') continue;
        char path[512];
        snprintf(path, sizeof(path), "%s/%s", dir, name);
        void* handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
        if (handle != NULL) {
            pos += snprintf(report + pos, sizeof(report) - (size_t) pos, "dlopen(%s): OK\n", name);
        } else {
            const char* err = dlerror();
            pos += snprintf(report + pos, sizeof(report) - (size_t) pos,
                            "dlopen(%s): FAILED (%s)\n", name, err != NULL ? err : "unknown");
        }
    }

    jstring result = (*env)->NewStringUTF(env, report);
    (*env)->ReleaseStringUTFChars(env, j_dir, dir);
    (*env)->ReleaseStringUTFChars(env, j_order, order);
    return result;
}
