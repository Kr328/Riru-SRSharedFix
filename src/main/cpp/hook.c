#include "hook.h"

#include <stdio.h>
#include <string.h>
#include <string.h>
#include <signal.h>

#define ELMLEN(a) (sizeof(a)/sizeof(*a))

// Patch Android Framework
static int android_runtime_start_reg_replaced(JNIEnv *env);
static int (*android_runtime_start_reg_original)(JNIEnv *env);

static jobject get_context_object_replaced(JNIEnv* env, jobject clazz);
static jobject (*get_context_object_original)(JNIEnv* env, jobject clazz);

static riru_utils_native_replace_t native_replace_list[] = {
	{".*android_runtime.*" ,"_ZN7android14AndroidRuntime8startRegEP7_JNIEnv" ,(void*)&android_runtime_start_reg_replaced ,(void**)&android_runtime_start_reg_original}
};

static riru_utils_jni_replace_method_t jni_replace_method_list[] = {
	{"com/android/internal/os/BinderInternal" ,"getContextObject" ,"()Landroid/os/IBinder;" ,(void*)&get_context_object_replaced ,(void**)&get_context_object_original}
};

int hook_install() {
    riru_utils_init_module("srsf");
	riru_utils_replace_native_functions(native_replace_list ,ELMLEN(native_replace_list));

	return 0;
}

// Logic Implemention
static int       class_loaded = 0;
static jmethodID java_get_context_object_method = NULL;
static jclass    java_inject_class        = NULL;

static jobject utils_get_context_object(JNIEnv* env, jobject clazz);
static jstring utils_get_current_config(JNIEnv *env ,jobject clazz);

static const JNINativeMethod java_inject_methods[] = {
	/* name, signature, funcPtr */
	{"getContextObjectOriginal","()Landroid/os/IBinder;", (void*)&utils_get_context_object}
};

static jobject utils_get_context_object(JNIEnv* env, jobject clazz) {
	return get_context_object_original(env ,clazz);
}

static int is_app_uid(int uid) {
    int appId = uid % 100000;

    // limit only regular app, or strange situation will happen, such as zygote process not start (dead for no reason and leave no clues?)
    // https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r8/core/java/android/os/UserHandle.java#151
    return appId >= 10000 && appId <= 19999;
}

static int android_runtime_start_reg_replaced(JNIEnv *env) {
	int result = android_runtime_start_reg_original(env);

	if ( !class_loaded ) {
	    riru_utils_replace_jni_methods(jni_replace_method_list ,ELMLEN(jni_replace_method_list) ,env);

		if ((java_inject_class = (*env)->FindClass(env,"com/github/kr328/srsf/Injector")) != NULL ) {
		    java_get_context_object_method = (*env)->GetStaticMethodID(env ,java_inject_class ,"getContextObjectReplaced" ,"()Landroid/os/IBinder;");
		    (*env)->RegisterNatives(env ,java_inject_class ,java_inject_methods ,ELMLEN(java_inject_methods));
		}
		else
		    LOGE("Find Class failure.");

		class_loaded = 1;
	}

	return result;
}

static jobject get_context_object_replaced(JNIEnv* env, jobject clazz) {
	if ( is_app_uid(getuid()) && java_get_context_object_method ) {
		//LOGI("Calling javaHookedGetContextObjectId");

		jobject result = (*env)->CallStaticObjectMethod(env ,java_inject_class ,java_get_context_object_method);

		if ( (*env)->ExceptionCheck(env) ) {
			(*env)->ExceptionDescribe(env);
			(*env)->ExceptionClear(env);
			LOGE("Call method failure");
		} else
			return result;
	}

	return get_context_object_original(env,clazz);
}
