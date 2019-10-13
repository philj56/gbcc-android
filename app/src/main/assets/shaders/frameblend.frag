#version 300 es

precision highp float;

in vec2 Texcoord;

out vec4 FragColor;

uniform sampler2D tex;

void main()
{
	vec4 old = texture(tex, Texcoord * vec2(0.5f, 1.0f) + vec2(0.5f, 0.0f));
	vec4 new = texture(tex, Texcoord * vec2(0.5f, 1.0f));
	FragColor = mix(new, old, 0.5);
}
