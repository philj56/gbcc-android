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
	#include <gbcc/window.h>
#pragma GCC visibility pop
}

pthread_t emu_thread;
struct gbcc gbc;
char *fname;

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_MyGLRenderer_initWindow(
	JNIEnv *env,
	jobject obj) {
	gbcc_window_initialise(&gbc);
	gbc.window.gl.cur_shader = 1;
	gbc.window.fps.show = true;
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
Java_com_philj56_gbcc_MyGLRenderer_clear(
		JNIEnv *env,
		jobject obj,/* this */
		jfloat x) {
	glClearColor(sin(x) * 0.5 + 0.5,
			(sin(x*1.1) * 0.5 + 0.5) * (cos(x*0.9) * 0.5 + 0.5),
			cos(x) * 0.5 + 0.5,
			1.0);
	// Redraw background colour
	glClear(GL_COLOR_BUFFER_BIT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_loadRom(
        JNIEnv *env,
        jobject obj,/* this */
        jstring file) {
    const char *string = env->GetStringUTFChars(file, nullptr);

    fname = (char *)malloc(strlen(string) + 1);
    memcpy(fname, string, strlen(string));
    fname[strlen(string)] = '\0';
    env->ReleaseStringUTFChars(file, string);

    gbcc_audio_initialise(&gbc);
    gbcc_initialise(&gbc.core, fname);
    gbc.quit = false;
    gbc.has_focus = true;

    pthread_create(&emu_thread, NULL, gbcc_emulation_loop, &gbc);

}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_quit(
	JNIEnv *env,
	jobject obj/* this */) {

	gbc.quit = true;
	pthread_join(emu_thread, NULL);
	__android_log_print(ANDROID_LOG_DEBUG, "GBCC", "Finished at 0x%04X", gbc.core.cpu.reg.pc);
	gbcc_free(&gbc.core);
	gbcc_audio_destroy(&gbc);
	free(fname);
}

extern "C" JNIEXPORT void JNICALL
Java_com_philj56_gbcc_GLActivity_press(
	JNIEnv *env,
	jobject obj/* this */,
	jint key) {
	switch (key) {
		case 0:
			gbc.core.keys.a = true;
			break;
		case 1:
			gbc.core.keys.b = true;
			break;
	}
}
