#include veil:color_utilities
#include pmweather:math

#define OFFSET 4.0

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D CloudSampler;
uniform sampler2D CloudBlurSampler;

uniform int shouldRender;

uniform vec2 OutSize;

uniform float downsample;
uniform int glowFix;
uniform int doBlur;

in vec2 texCoord;

out vec4 fragColor;

vec4 mostOpaque(vec4 a, vec2 uv, float depth){
    vec2 mainUV = uv * downsample;
    float newDepth = texture(DiffuseDepthSampler, mainUV).r;
    vec4 b = texture(CloudBlurSampler, uv);
    return mix(a, b, smoothstep(a.a-0.05, a.a+0.05, b.a)*step(newDepth, depth));
}

void main(){
    vec3 col = texture(DiffuseSampler0, texCoord).rgb;

    if(shouldRender == 0){
        fragColor = vec4(col, 1.0);
        return;
    }

    vec2 cloudUV = texCoord / downsample;
    vec3 halfPixel = vec3(0.5 / (OutSize * downsample), 0.0);
    halfPixel.y = -halfPixel.y;

    float depth = texture(DiffuseDepthSampler, texCoord).r;

    vec4 sum = texture(CloudBlurSampler, cloudUV);

    if(glowFix == 1){
        sum = mostOpaque(sum, cloudUV + halfPixel.xz * OFFSET, depth);
        sum = mostOpaque(sum, cloudUV + halfPixel.yz * OFFSET, depth);
        sum = mostOpaque(sum, cloudUV + halfPixel.zy * OFFSET, depth);
        sum = mostOpaque(sum, cloudUV + halfPixel.zx * OFFSET, depth);
    }

    vec4 renderOut = sum;

    col = col * pow3_4(1.0-clamp01(renderOut.a)) + renderOut.rgb;

    fragColor = vec4(col, 1.0);
}