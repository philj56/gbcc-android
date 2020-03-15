#version 300 es

precision highp float;

in vec2 Texcoord;

out vec4 FragColor;

uniform sampler2D tex;
uniform sampler2D last_tex;
uniform float odd_frame;
uniform bool interlacing;
uniform bool frameblending;

void main()
{
	vec4 old = texture(last_tex, Texcoord);
	vec4 new = texture(tex, Texcoord);

	if (interlacing) {
		float darken = floor(mod(Texcoord.y * 144.0 + odd_frame, 2.0));
		new *= darken * 0.5 + 0.5;
		darken = floor(mod(Texcoord.y * 144.0 + odd_frame + 1.0, 2.0));
		old *= darken * 0.5 + 0.5;
	}
	if (frameblending) {
		FragColor = mix(new, old, 0.5);
	} else {
		FragColor = new;
	}
}
