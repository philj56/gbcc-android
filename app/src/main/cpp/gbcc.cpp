#include <jni.h>

#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>

#include <math.h>

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