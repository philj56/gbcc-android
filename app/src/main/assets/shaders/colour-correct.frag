#version 300 es

precision highp float;
precision highp sampler3D;

in vec2 Texcoord;

out vec4 FragColor;

uniform sampler2D tex;
uniform sampler3D lut;

void main()
{
	vec4 icol = texture(tex, Texcoord);
	// TODO: This factor is a hold-over from the old colour correction
	// code, when I was manually interpolating colours. Is it correct /
	// needed anymore?
	const float brightening = 35.0 / 31.0;
	FragColor = vec4(texture(lut, icol.rgb).rgb, 1) * brightening;
}
