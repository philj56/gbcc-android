#include <jni.h>

#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>

#include <string>
#include <cmath>
#include <android/log.h>
#include <pthread.h>

extern "C" {
#pragma GCC visibility push(hidden)
	#include <gbcc/gbcc.h>
	#include <gbcc/core.h>
	#include <gbcc/save.h>
	#include <gbcc/window.h>
#pragma GCC visibility pop
}

pthread_t emu_thread;
struct gbcc gbc;
char *fname;

void update_preferences(JNIEnv *env, jobject prefs) {
	jstring ret;
	jstring arg;
	jmethodID id;
	jclass prefsClass = env->GetObjectClass(prefs);

	id = env->GetMethodID(prefsClass, "getBoolean", "(Ljava/lang/String;Z)Z");
	arg = env->NewStringUTF("show_fps");
	gbc.window.fps.show = env->CallBooleanMethod(prefs, id, arg, false);
	env->DeleteLocalRef(arg);


	id = env->GetMethodID(prefsClass, "getString", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
	arg = env->NewStringUTF("shader");
	ret = (jstring)env->CallObjectMethod(prefs, id, arg, NULL);

	const char *shader_name = env->GetStringUTFChars(ret, nullptr);
	gbcc_window_use_shader(&gbc, shader_name);
	env->DeleteLocalRef(arg);
	env->ReleaseStringUTFChars(ret, shader_name);


	env->DeleteLocalRef(prefsClass);
}

bool check_autoresume(JNIEnv *env, jobject prefs) {
	bool ret;
	jstring arg;
	jmethodID id;
	jclass prefsClass = env->GetObjectClass(prefs);

	id = env->GetMethodID(prefsClass, "getBoolean", "(Ljava/lang/String;Z)Z");
	arg = env->NewStringUTF("auto_resume");
	ret = env->CallBooleanMethod(prefs, id, arg, false);
	env->DeleteLocalRef(arg);

	env->DeleteLocalRef(prefsClass);
	return ret;
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_MyGLRenderer_initWindow(
	JNIEnv *env,
	jobject obj,
	jobject prefs) {
	gbcc_window_initialise(&gbc);
	if (gbc.core.initialised) {
		update_preferences(env, prefs);
	}
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_MyGLRenderer_updateWindow(
	JNIEnv *env,
	jobject obj) {
	if (gbc.core.initialised) {
		gbcc_window_update(&gbc);
	}
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_MyGLRenderer_resizeWindow(
	JNIEnv *env,
	jobject obj,
	jint width,
	jint height) {
	__android_log_print(ANDROID_LOG_DEBUG, "GBCC", "Resized to %dx%d", width, height);
	gbc.window.width = width;
	gbc.window.height = height;
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_loadRom(
        JNIEnv *env,
        jobject obj,/* this */
        jstring file,
        jobject prefs) {

	const char *string = env->GetStringUTFChars(file, nullptr);

	fname = (char *)malloc(strlen(string) + 1);
	memcpy(fname, string, strlen(string));
	fname[strlen(string)] = '\0';
	env->ReleaseStringUTFChars(file, string);

	gbcc_audio_initialise(&gbc);
	gbcc_initialise(&gbc.core, fname);
	gbc.quit = false;
	gbc.has_focus = true;

	__android_log_print(ANDROID_LOG_INFO, "GBCC", "%s", fname);
	if (gbc.window.initialised) {
		update_preferences(env, prefs);
	}
	if (check_autoresume(env, prefs)) {
		gbc.load_state = 10;
	}
	pthread_create(&emu_thread, nullptr, gbcc_emulation_loop, &gbc);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_quit(
	JNIEnv *env,
	jobject obj/* this */) {

	gbc.quit = true;
	pthread_join(emu_thread, nullptr);
	gbcc_free(&gbc.core);
	gbcc_audio_destroy(&gbc);
	free(fname);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_press(
	JNIEnv *env,
	jobject obj/* this */,
	jint key,
	jboolean pressed) {
	switch (key) {
		case 0:
			gbc.core.keys.a = pressed;
			break;
		case 1:
			gbc.core.keys.b = pressed;
			break;
		case 2:
			gbc.core.keys.start = pressed;
			break;
		case 3:
			gbc.core.keys.select = pressed;
			break;
		case 4:
			gbc.core.keys.dpad.up = pressed;
			break;
		case 5:
			gbc.core.keys.dpad.down = pressed;
			break;
		case 6:
			gbc.core.keys.dpad.left = pressed;
			break;
		case 7:
			gbc.core.keys.dpad.right = pressed;
			break;
	}
}


extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_saveState(
	JNIEnv *env,
	jobject obj/* this */,
	jint state) {
	gbc.save_state = state;
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_loadState(
	JNIEnv *env,
	jobject obj/* this */,
	jint state) {
	gbc.load_state = state;
}