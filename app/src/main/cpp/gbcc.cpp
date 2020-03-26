/*
 * Copyright (C) 2019-2020 Philip Jones
 *
 * Licensed under the MIT License.
 * See either the LICENSE file, or:
 *
 * https://opensource.org/licenses/MIT
 *
 */

#include <jni.h>

#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>

#include <string>
#include <cmath>
#include <android/log.h>
#include <pthread.h>
#include <semaphore.h>

extern "C" {
#pragma GCC visibility push(hidden)
	#include <gbcc.h>
	#include <core.h>
	#include <save.h>
	#include <window.h>
#pragma GCC visibility pop
}

#define MAX_SHADER_LEN 32

pthread_t emu_thread;
struct gbcc gbc;
char *fname;
char shader[MAX_SHADER_LEN];


void update_preferences(JNIEnv *env, jobject prefs) {
	jstring ret;
	jstring arg;
	jmethodID id;
	jclass prefsClass = env->GetObjectClass(prefs);

	id = env->GetMethodID(prefsClass, "getBoolean", "(Ljava/lang/String;Z)Z");
	arg = env->NewStringUTF("auto_save");
	gbc.autosave = env->CallBooleanMethod(prefs, id, arg, false);
	env->DeleteLocalRef(arg);
	arg = env->NewStringUTF("frame_blend");
	gbc.frame_blending = env->CallBooleanMethod(prefs, id, arg, false);
	env->DeleteLocalRef(arg);
	arg = env->NewStringUTF("vsync");
	gbc.core.sync_to_video = env->CallBooleanMethod(prefs, id, arg, false);
	env->DeleteLocalRef(arg);
	arg = env->NewStringUTF("interlacing");
	gbc.interlacing = env->CallBooleanMethod(prefs, id, arg, false);
	env->DeleteLocalRef(arg);
	arg = env->NewStringUTF("show_fps");
	gbc.show_fps = env->CallBooleanMethod(prefs, id, arg, false);
	env->DeleteLocalRef(arg);

	id = env->GetMethodID(prefsClass, "getString", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
	arg = env->NewStringUTF("turbo_speed");
	ret = (jstring)env->CallObjectMethod(prefs, id, arg, NULL);
	if (ret != NULL) {
		const char *tmp = env->GetStringUTFChars(ret, nullptr);
		gbc.turbo_speed = static_cast<float>(atof(tmp));
		env->ReleaseStringUTFChars(ret, tmp);
	}
	env->DeleteLocalRef(arg);

	if (gbc.core.mode == GBC) {
		arg = env->NewStringUTF("shader_gbc");
	} else {
		arg = env->NewStringUTF("shader_dmg");
	}
	ret = (jstring)env->CallObjectMethod(prefs, id, arg, NULL);

	if (ret == NULL) {
		if (gbc.core.mode == GBC) {
			strncpy(shader, "Subpixel", MAX_SHADER_LEN);
		} else {
			strncpy(shader, "Dot Matrix", MAX_SHADER_LEN);
		}
	} else {
		const char *name = env->GetStringUTFChars(ret, nullptr);
		strncpy(shader, name, MAX_SHADER_LEN);
		env->ReleaseStringUTFChars(ret, name);
	}
	env->DeleteLocalRef(arg);
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
	gbcc_menu_init(&gbc);
	gbcc_menu_update(&gbc);
	if (gbc.core.initialised) {
		update_preferences(env, prefs);
		gbcc_window_use_shader(&gbc, shader);
	}

}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_MyGLRenderer_updateWindow(
	JNIEnv *env,
	jobject obj) {
	if (!gbc.window.initialised) {
		gbcc_window_initialise(&gbc);
		gbcc_window_use_shader(&gbc, shader);
	}
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
	sem_post(&gbc.core.ppu.vsync_semaphore);
	pthread_join(emu_thread, nullptr);
	gbcc_free(&gbc.core);
	gbcc_audio_destroy(&gbc);
	gbcc_window_deinitialise(&gbc);
	free(fname);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_toggleMenu(
	JNIEnv *env,
	jobject obj/* this */,
	jobject view) {
	gbcc_input_process_key(&gbc, GBCC_KEY_MENU, true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_toggleTurbo(
	JNIEnv *env,
	jobject obj/* this */) {
	gbcc_input_process_key(&gbc, GBCC_KEY_TURBO, true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_press(
	JNIEnv *env,
	jobject obj/* this */,
	jint key,
	jboolean pressed) {
	switch (key) {
		case 0:
			gbcc_input_process_key(&gbc, GBCC_KEY_A, pressed);
			break;
		case 1:
			gbcc_input_process_key(&gbc, GBCC_KEY_B, pressed);
			break;
		case 2:
			gbcc_input_process_key(&gbc, GBCC_KEY_START, pressed);
			break;
		case 3:
			gbcc_input_process_key(&gbc, GBCC_KEY_SELECT, pressed);
			break;
		case 4:
			gbcc_input_process_key(&gbc, GBCC_KEY_UP, pressed);
			break;
		case 5:
			gbcc_input_process_key(&gbc, GBCC_KEY_DOWN, pressed);
			break;
		case 6:
			gbcc_input_process_key(&gbc, GBCC_KEY_LEFT, pressed);
			break;
		case 7:
			gbcc_input_process_key(&gbc, GBCC_KEY_RIGHT, pressed);
			break;
		default:
			break;
	}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_philj56_gbcc_GLActivity_isPressed(
	JNIEnv *env,
	jobject obj/* this */,
	jint key) {
	switch (key) {
		case 0:
			return static_cast<jboolean>(gbc.core.keys.a);
		case 1:
			return static_cast<jboolean>(gbc.core.keys.b);
		case 2:
			return static_cast<jboolean>(gbc.core.keys.start);
		case 3:
			return static_cast<jboolean>(gbc.core.keys.select);
		case 4:
			return static_cast<jboolean>(gbc.core.keys.dpad.up);
		case 5:
			return static_cast<jboolean>(gbc.core.keys.dpad.down);
		case 6:
			return static_cast<jboolean>(gbc.core.keys.dpad.left);
		case 7:
			return static_cast<jboolean>(gbc.core.keys.dpad.right);
		default:
			return static_cast<jboolean>(false);
	}
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_saveState(
	JNIEnv *env,
	jobject obj/* this */,
	jint state) {
	gbc.save_state = static_cast<int8_t>(state);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_loadState(
	JNIEnv *env,
	jobject obj/* this */,
	jint state) {
	gbc.load_state = static_cast<int8_t>(state);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_philj56_gbcc_GLActivity_checkVibrationFun(
	JNIEnv *env,
	jobject obj/* this */) {
	static bool last_rumble = false;
	bool rumble = gbc.core.cart.rumble_state;
	bool ret = (rumble != last_rumble);
	last_rumble = rumble;
	return static_cast<jboolean>(ret);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_updateAccelerometer(
	JNIEnv *env,
	jobject obj/* this */,
	jfloat x,
	jfloat y) {
	const float g = 9.81;
	gbc.core.cart.mbc.accelerometer.real_x = static_cast<uint16_t>(0x81D0u + (0x70 * x / g));
	gbc.core.cart.mbc.accelerometer.real_y = static_cast<uint16_t>(0x81D0u - (0x70 * y / g));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_philj56_gbcc_GLActivity_hasRumble(
	JNIEnv *env,
	jobject obj/* this */) {
	return static_cast<jboolean>(gbc.core.cart.rumble);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_philj56_gbcc_GLActivity_hasAccelerometer(
	JNIEnv *env,
	jobject obj/* this */) {
	return static_cast<jboolean>(gbc.core.cart.mbc.type == MBC7);
}
