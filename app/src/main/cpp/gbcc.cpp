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
#include <unistd.h>

extern "C" {
#pragma GCC visibility push(hidden)
	#include <gbcc.h>
	#include <camera.h>
	#include <core.h>
	#include <save.h>
	#include <window.h>
#pragma GCC visibility pop
}

#define MAX_SHADER_LEN 32
#define MAX(a, b) ((a) > (b) ? (a) : (b))
#define MIN(a, b) ((a) < (b) ? (a) : (b))

static pthread_t emu_thread;
static struct gbcc gbc;
static char *fname;
static char *save_dir;
static char shader[MAX_SHADER_LEN];
static gbcc_fontmap fontmap;
static uint8_t camera_image[GB_CAMERA_SENSOR_SIZE];
static pthread_mutex_t render_mutex = PTHREAD_MUTEX_INITIALIZER;

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

	arg = env->NewStringUTF("palette");
	ret = (jstring)env->CallObjectMethod(prefs, id, arg, NULL);
	if (ret != NULL) {
		const char *tmp = env->GetStringUTFChars(ret, nullptr);
		gbc.core.ppu.palette = gbcc_get_palette(tmp);
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
		jobject obj) {
	gbcc_window_initialise(&gbc);
	gbcc_window_use_shader(&gbc, shader);
	gbcc_menu_init(&gbc);
	gbcc_menu_update(&gbc);
}


extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_MyGLSurfaceView_destroyWindow(
		JNIEnv *env,
		jobject obj) {
	if (gbc.window.initialised) {
		gbcc_menu_destroy(&gbc);
		// Have to destroy the window manually, as the OpenGL context is likely to be gone by now
		gbc.window.initialised = false;
	}
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_MyGLRenderer_updateWindow(
		JNIEnv *env,
		jobject obj) {
	if (gbc.core.initialised) {
		if (!gbc.window.initialised) {
			gbcc_window_initialise(&gbc);
		}
		if (pthread_mutex_trylock(&render_mutex) != 0) {
			return;
		}
		gbcc_window_update(&gbc);
		pthread_mutex_unlock(&render_mutex);
	}
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_MyGLRenderer_resizeWindow(
		JNIEnv *env,
		jobject obj,
		jint width,
		jint height) {
	gbc.window.width = width;
	gbc.window.height = height;
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_chdir(
		JNIEnv *env,
		jobject obj,/* this */
		jstring dirName) {
	const char *path = env->GetStringUTFChars(dirName, nullptr);
	chdir(path);
	env->ReleaseStringUTFChars(dirName, path);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_philj56_gbcc_GLActivity_checkRom(
		JNIEnv *env,
		jobject obj,/* this */
		jstring file) {
	const char *string = env->GetStringUTFChars(file, nullptr);
	gbcc_initialise(&gbc.core, string);
	env->ReleaseStringUTFChars(file, string);
	bool ret = gbc.core.initialised;
	if (gbc.core.initialised) {
		gbcc_free(&gbc.core);
	}
	return static_cast<jboolean>(ret);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_philj56_gbcc_GLActivity_getErrorMessage(
		JNIEnv *env,
		jobject obj/* this */) {
	return env->NewStringUTF(gbc.core.error_msg);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_philj56_gbcc_GLActivity_loadRom(
		JNIEnv *env,
		jobject obj,/* this */
		jstring file,
		jint sampleRate,
		jint samplesPerBuffer,
		jstring saveDir,
		jobject prefs) {

	const char *string = env->GetStringUTFChars(file, nullptr);

	size_t name_len = strlen(string) + 1;
	fname = (char *)malloc(name_len);
	memcpy(fname, string, name_len);
	env->ReleaseStringUTFChars(file, string);

	string = env->GetStringUTFChars(saveDir, nullptr);
	name_len = strlen(string) + 1;
	save_dir = (char *)malloc(name_len);
	memcpy(save_dir, string, name_len);
	env->ReleaseStringUTFChars(saveDir, string);

	gbcc_initialise(&gbc.core, fname);
	if (!gbc.core.initialised) {
		/* Something went wrong during initialisation */
		free(fname);
		free(saveDir);
		return static_cast<jboolean>(false);
	}
	gbc.quit = false;
	gbc.has_focus = true;
	gbc.save_directory = save_dir;
	gbcc_audio_initialise(&gbc, static_cast<size_t>(sampleRate), static_cast<size_t>(samplesPerBuffer));

	__android_log_print(ANDROID_LOG_INFO, "GBCC", "%s", fname);
	update_preferences(env, prefs);
	if (check_autoresume(env, prefs)) {
		gbc.load_state = 10;
	}
	pthread_create(&emu_thread, nullptr, gbcc_emulation_loop, &gbc);
	return static_cast<jboolean>(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_quit(
		JNIEnv *env,
		jobject obj/* this */) {

	gbc.quit = true;
	sem_post(&gbc.core.ppu.vsync_semaphore);
	pthread_join(emu_thread, nullptr);

	// Don't allow the screen to be drawn to while we're freeing the core
	const struct timespec wait_time = { .tv_sec = 0, .tv_nsec = 100000000 };
	if (pthread_mutex_timedlock(&render_mutex, &wait_time) == 0) {
		gbcc_free(&gbc.core);
		pthread_mutex_unlock(&render_mutex);
	} else {
		// If we failed to acquire the lock, just free anyway
		gbcc_free(&gbc.core);
	}
	gbcc_audio_destroy(&gbc);
	free(fname);
	free(save_dir);
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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_philj56_gbcc_GLActivity_isCamera(
		JNIEnv *env,
		jobject obj/* this */) {
	return static_cast<jboolean>(gbc.core.cart.mbc.type == CAMERA);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_updateCamera(
		JNIEnv *env,
		jobject obj/* this */,
		jbyteArray array,
		jint width,
		jint height,
		jint rotation,
		jint rowStride) {
	jbyte *image = env->GetByteArrayElements(array, NULL);

	// Perform box-blur downsampling of the sensor image to get out 128x128 gb camera image
	int box_size = MIN(width, height) / 128 / 2 + 1;

	// First we perform the horizontal blur
	uint8_t *new_row = static_cast<uint8_t *>(calloc(width, 1));
	for (int j = 0; j < height; j++) {
		jbyte *row = &image[j * rowStride];
		int sum = 0;
		int div = 0;

		for (int i = -box_size; i < width; i++) {
			if (i < width - box_size) {
				sum += static_cast<uint8_t>(row[i + box_size]);
			} else {
				div--;
			}
			if (i >= box_size) {
				sum -= static_cast<uint8_t>(row[i - box_size]);
			} else {
				div++;
			}

			if (i >= 0) {
				new_row[i] = static_cast<uint8_t>(sum / div);
			}
		}
		memcpy(row, new_row, width);
	}
	free(new_row);

	// Then the vertical blur
	uint8_t *new_col = static_cast<uint8_t *>(calloc(height, 1));
	for (int i = 0; i < width; i++) {
		int sum = 0;
		int div = 0;

		for (int j = -box_size; j < height; j++) {
			if (j < height - box_size) {
				sum += static_cast<uint8_t>(image[(j + box_size) * rowStride + i]);
			} else {
				div--;
			}
			if (j >= box_size) {
				sum -= static_cast<uint8_t>(image[(j - box_size) * rowStride + i]);
			} else {
				div++;
			}

			if (j >= 0) {
				new_col[j] = static_cast<uint8_t>(sum / div);
			}
		}
		for (int j = 0; j < height; j++) {
			image[j * rowStride + i] = new_col[j];
		}
	}
	free(new_col);

	// Then we nearest-neighbour downscale and rotate
	double scale = MIN(height, width) / 128.0;
	for (int j = 0; j < 128; j++) {
		for (int i = 0; i < 128; i++) {
			int src_idx = (int)(j * scale) * rowStride + (int)(i * scale);
			int dst_idx;
			if (rotation == 90) {
				dst_idx = i * 128 + (127 - j);
			} else if (rotation == 180) {
				dst_idx = (127 - j) * 128 + (127 - i);
			} else if (rotation == 270) {
				dst_idx = (127 - i) * 128 + j;
			} else {
				dst_idx = j * 128 + i;
			}

			camera_image[dst_idx] = static_cast<uint8_t>(image[src_idx]);
		}
	}

	env->ReleaseByteArrayElements(array, image, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_initialiseTileset(
		JNIEnv *env,
		jobject obj,/* this */
		jint width,
		jint height,
		jbyteArray data) {
	fontmap.bitmap = static_cast<uint8_t *>(calloc(width * height, 1));
	fontmap.tile_width = width / 16;
	fontmap.tile_height = height / 16;
	jbyte *image = env->GetByteArrayElements(data, NULL);
	// Have to use a for loop as the data is converted to int
	// when loaded by Android, even though it's greyscale
	for (int j = 0; j < height; j++) {
		for (int i = 0; i < width; i++) {
			int idx = j * width + i;
			fontmap.bitmap[idx] = static_cast<uint8_t>(image[4 * idx]);
		}
	}
	env->ReleaseByteArrayElements(data, image, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_destroyTileset(
		JNIEnv *env,
		jobject obj/* this */) {
	free(fontmap.bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_setCameraImage(
		JNIEnv *env,
		jobject obj,/* this */
		jbyteArray data) {
	jbyte *image = env->GetByteArrayElements(data, NULL);
	// Have to use a for loop as the data is converted to int
	// when loaded by Android, even though it's greyscale
	for (int j = 0; j < GB_CAMERA_SENSOR_HEIGHT; j++) {
		for (int i = 0; i < GB_CAMERA_SENSOR_WIDTH; i++) {
			int idx = j * GB_CAMERA_SENSOR_WIDTH + i;
			camera_image[idx] = static_cast<uint8_t>(image[4 * idx]);
		}
	}
	env->ReleaseByteArrayElements(data, image, JNI_ABORT);
}

void gbcc_camera_platform_initialise(struct gbcc_camera_platform *camera) {
	(void) camera;
}

void gbcc_camera_platform_destroy(struct gbcc_camera_platform *camera) {
	(void) camera;
}

void gbcc_camera_platform_capture_image(struct gbcc_camera_platform *camera, uint8_t image[GB_CAMERA_SENSOR_SIZE]) {
	(void) camera;
	memcpy(image, camera_image, GB_CAMERA_SENSOR_SIZE);
}

extern "C" void gbcc_screenshot(struct gbcc *gbc) {
    return;
}

extern "C" void gbcc_fontmap_load(struct gbcc_fontmap *font) {
    font->bitmap = fontmap.bitmap;
    font->tile_width = fontmap.tile_width;
	font->tile_height = fontmap.tile_height;
	return;
}

extern "C" void gbcc_fontmap_destroy(struct gbcc_fontmap *font) {
	return;
}
