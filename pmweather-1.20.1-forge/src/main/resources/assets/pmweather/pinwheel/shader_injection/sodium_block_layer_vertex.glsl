#include pmweather:wind
#include pmweather:sway

uniform sampler2D _pmwWindTex;
uniform sampler2D _pmwPrevWindTex;

uniform float VeilRenderTime;
uniform float pmwtime = 0.0;
uniform vec3 cameraPos;
uniform int doesSway = 0;

uniform vec3 fboOffset;
uniform vec3 prevFboOffset;
uniform float fboTimeDelta;

in float pmw_blockId;
in uvec2 pmw_midBlock;

void tail(){
    if(doesSway == 0 || pmw_blockId <= -5.0) return;

    vec3 midBlock = (_deinterleave_u20x3(pmw_midBlock) * VERTEX_SCALE) + VERTEX_OFFSET;
    midBlock -= 1.0;
    midBlock *= -1.0;

    vec3 blockPos = position - midBlock;
    vec3 worldPos = position + cameraPos;
    vec3 blockWorldPos = blockPos + cameraPos;
    vec3 vertLocal = position - u_RegionOffset;

    float height = 0.1;
    float realHeight = -midBlock.y + 0.5;
    height += realHeight;

    float heightId = pmw_blockId;

    if(heightId >= 1.0){
        height += heightId;
        realHeight += heightId;
        height = min(height, 2.1);
    }

    if(heightId >= 0.0){
        height /= 4.0;
        height *= height;
    }

    vec3 wind = getWind(_pmwWindTex, _pmwPrevWindTex, position, fboOffset, prevFboOffset, fboTimeDelta);
    wind *= vec3(1.5,0.5,1.5);
    vec3 windRaw = wind;

    float timeOff = pmwtime/30.0;
    vec3 noisePos = vec3(worldPos.xz, timeOff);
    vec3 blockNoisePos = vec3(blockWorldPos.xz, timeOff);

    float xOff = cfbm(noisePos.xyz/5.0, 5, 2.0, 0.5, 1.0);
    float yOff = cfbm(noisePos.yzx/5.0, 5, 2.0, 0.5, 1.0);
    float zOff = cfbm(noisePos.zxy/5.0, 5, 2.0, 0.5, 1.0);

    wind += vec3(xOff, (yOff+0.5)/12.0, zOff)*max(length(wind)/3.0, 15.0)/(height+1.0);
    wind.y += length(wind.xz)/8.0;

    float hourNoise = cfbm((blockNoisePos/100000.0) + (vertLocal / 50.0), 5, 2.0, 0.5, 1.0);
    float hour = VeilRenderTime/3600.0;
    hour += hourNoise;

    position += getSwayOffset(hour, wind, windRaw, height, realHeight);

    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);
}