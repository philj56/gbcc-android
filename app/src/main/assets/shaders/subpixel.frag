#version 300 es

precision highp float;

in vec2 Texcoord;

out vec4 out_colour;

uniform sampler2D tex;

const vec3 r = vec3(255, 113, 69) / 255.0;
const vec3 g = vec3(193, 214, 80) / 255.0;
const vec3 b = vec3(59, 206, 255) / 255.0;

const float radius = 2.0;
const float radius2 = radius * radius;

vec3 circ(vec3 x)
{
	return sqrt(max(radius2 - x * x, 0.0)) / radius;
}

void main()
{
	vec3 src;
	src.r = texture(tex, Texcoord + vec2(1.0 / 480.0, 0)).r;
	src.g = texture(tex, Texcoord).g;
	src.b = texture(tex, Texcoord - vec2(1.0 / 480.0, 0)).b;
	vec3 x = mod(Texcoord.x * 160.0 * 7.0 + vec3(3, 1, 4), 7.0) - vec3(3, 3, 1);
	vec3 weight = circ(x);
	float y = mod(Texcoord.y * 144.0 * 7.0, 7.0);
	vec3 dst = vec3(0);
	dst += src.r * r * weight.r;
	dst += src.g * g * weight.g;
	dst += src.b * b * weight.b;
	float gridline = max(ceil((y - 1.0) / 6.0), 0.7);
	out_colour = vec4(gridline * dst, 1.0);
}
