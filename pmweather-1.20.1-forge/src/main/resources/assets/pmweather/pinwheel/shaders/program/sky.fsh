#include veil:space_helper
#include veil:color_utilities
#include pmweather:math
#include pmweather:noise
#include pmweather:colors

uniform sampler2D DiffuseSampler0;
uniform sampler2D MoonSampler;
uniform sampler2D NightSkySampler;

uniform int shouldRender;

uniform vec3 sunDir;
uniform vec3 lightingColor;
uniform float lunarPhase;

uniform vec4 FogColor;

in vec2 texCoord;

out vec4 fragColor;

float getTerminator(float y, float size, float power){
    float f = cos(PI*lunarPhase);
    f = pow(f, power)*sign(f);
    return f*sqrt((size*size)-(y*y));
}

void main(){
    if(shouldRender == 0){
        fragColor = texture(DiffuseSampler0, texCoord);
        return;
    }

    vec3 dir = viewDirFromUv(texCoord);
    float height = clamp(dir.y, 0.0, 1.0);

    float sunRisePerc = min(sunDir.y + 0.25, 1.0);
    float nSRP = clamp(sunRisePerc, 0.0, 1.0);
    vec3 curSkyColor = mix(vec3(0.015), mix(mix(FogColor.rgb*0.9, skyColor,  0.45), mix(FogColor.rgb*0.6, upperSkyColor, 0.45), sqrt(height)), sqrt(nSRP));
    curSkyColor *= mix(1.0, mix(0.5, 0.75, nSRP*nSRP), height*height);

    float sunPerc = dot(sunDir, dir);
    float moonPerc = -sunPerc;

    float horizonLightPerc = sunPerc+1.0;
    float hOff = (1.0-(horizonLightPerc/2.0))*0.1;
    float decay = 1.0-clamp((height+hOff)*2.0, 0.0, 1.0);
    horizonLightPerc *= decay*decay;
    horizonLightPerc *= 1.0-clamp(abs(sunDir.y)*2.5, 0.0, 1.0);
    horizonLightPerc *= sqrt(sqrt(sqrt(nSRP)));

    float sunBloom = clamp((sunPerc-0.92)*12.5, 0.0, 1.0);
    horizonLightPerc += sunBloom*sunBloom*pow(1.0-max(sunDir.y, 0.0), 2.0)*0.75;

    horizonLightPerc *= clamp(sunRisePerc*10.0, 0.0, 1.0);

    curSkyColor = mix(curSkyColor, mix(sunsetSecondary, lightingColor, clamp(sqrt(horizonLightPerc)*0.48, 0.0, 1.0))*1.15, horizonLightPerc);

    float sunGlow = clamp((sunPerc-0.98)*50.0, 0.0, 1.0);
    float physicalSun = clamp((sunPerc-0.999)*1000.0, 0.0, 1.0);
    curSkyColor += lightingColor*mix(1.5, 0.75, sqrt(sqrt(nSRP)))*pow(sunGlow, 10.0);
    curSkyColor = mix(curSkyColor, lightingColor + vec3(1.5), sqrt(physicalSun));
    bool blockedByMoon = false;

    if(moonPerc > 0.0){
        float moonSize = 0.1;
        vec3 plane = cross(vec3(0.0, 0.0, 1.0), -sunDir);
        float v = dot(plane, dir);
        vec2 moonUV = clamp(vec2(dir.z, v)/moonSize, vec2(-0.5), vec2(0.5))+vec2(0.5);
        vec2 moonUVCentered = (moonUV-vec2(0.5))*2.0;

        if(length(moonUVCentered) < 1.0){
            float moonTex = texture(MoonSampler, moonUV).r;
            float moonBright = moonTex;
            float terminator = getTerminator(moonUVCentered.y, 1.0, 2.0);
            float terminator2 = -terminator;
            float axis = moonUVCentered.x;

            float fadeDist = 0.05;

            if(lunarPhase <= 0.0){
                float dist = axis-terminator;
                dist -= mix(fadeDist, 0.0, -lunarPhase);
                float distPerc = clamp(dist/fadeDist, 0.0, 1.0);
                moonBright *= distPerc;
            }else{
                float dist = axis-terminator2;
                dist -= mix(fadeDist, 0.0, 1.0-lunarPhase);
                float distPerc = 1.0-clamp(dist/fadeDist, 0.0, 1.0);
                moonBright *= distPerc;
            }

            float d = length(moonUVCentered);

            float rimLighting = pow(clamp((d-0.8)*8.0, 0.0, 1.0), 2.0)*moonTex;
            rimLighting *= pow(1.0-abs(lunarPhase), 2.0);
            moonBright += rimLighting*0.3;

            curSkyColor = mix(curSkyColor, vec3(1.0), clamp(moonBright, 0.0, 1.0));

            blockedByMoon = moonTex > 0.0;
        }

        float moonGlow = clamp((moonPerc-0.96)*25.0, 0.0, 1.0)*sqrt(sqrt(sqrt(abs(lunarPhase))));
        curSkyColor = mix(curSkyColor, vec3(1.0), pow(moonGlow, 15.0)*0.65);
    }

    if(!blockedByMoon){
        float sunAng = atan2(sunDir.y, sunDir.x);
        vec3 nightSkyDir = rotate(rotate(dir, vec3(1.0, 0.0, 0.0), PI/4.0), vec3(0.0, 0.0, 1.0), sunAng+(PI/8.0));
        vec2 nightSkyUV = normalVec3ToLambertUV(nightSkyDir);
        vec4 nightSky = texture(NightSkySampler, nightSkyUV);
        nightSky.a *= luminance(nightSky.rgb);

        float nightPerc = max(-sunDir.y, 0.0);
        nightSky.a *= sqrt(sqrt(nightPerc));
        curSkyColor = mix(curSkyColor, nightSky.rgb, clamp(nightSky.a, 0.0, 1.0));
    }

    fragColor = vec4(curSkyColor, 1.0);
}