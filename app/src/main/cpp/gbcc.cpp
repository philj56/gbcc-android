#include <jni.h>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <math.h>
extern "C" {
#include "gbcc/src/wav.h"
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_opengltest_MyGLRenderer_clear(
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
Java_com_example_opengltest_MyGLRenderer_test(
		JNIEnv *env,
		jobject obj /* this */) {
	struct wav_header wav = {0};
	wav_print_header(&wav);
}
