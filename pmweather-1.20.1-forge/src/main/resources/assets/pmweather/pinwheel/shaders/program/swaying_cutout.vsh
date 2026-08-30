#include veil:light
#include veil:fog
#include pmweather:wind
#include pmweather:sway

#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
layout(location = 4) in vec3 Normal;

layout(location = 5) in vec3 pmw_blockId;
layout(location = 6) in vec3 pmw_midBlock;

uniform sampler2D Sampler2;
uniform sampler2D LocalWindSampler;
uniform sampler2D LocalWindSamplerPrevious;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform float VeilRenderTime;
uniform float time;
uniform int doesSway = 0;

uniform vec3 fboOffset;
uniform vec3 prevFboOffset;
uniform float fboTimeDelta;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main(){
    vec3 pos = Position + ChunkOffset;

    if(doesSway == 1 && pmw_blockId.x > -5.0){
        vec3 blockPos = pos - (pmw_midBlock / 64.0);
        vec3 worldPos = pos + VeilCamera.CameraPosition;
        vec3 blockWorldPos = blockPos + VeilCamera.CameraPosition;

        float height = 0.1;
        float realHeight = (-pmw_midBlock.y / 64.0) + 0.5;
        height += realHeight;

        float heightId = pmw_blockId.x;

        if(heightId >= 1.0){
            height += heightId;
            realHeight += heightId;
            height = min(height, 2.1);
        }

        if(heightId >= 0.0){
            height /= 4.0;
            height *= height;
        }

        vec3 wind = getWind(LocalWindSampler, LocalWindSamplerPrevious, pos, fboOffset, prevFboOffset, fboTimeDelta);
        wind *= vec3(1.5,0.5,1.5);
        vec3 windRaw = wind;

        float timeOff = time/30.0;
        vec3 noisePos = vec3(mod(worldPos.xz, 4096.0), timeOff);
        vec3 blockNoisePos = vec3(mod(blockWorldPos.xz, 4096.0), timeOff);

        float xOff = cfbm(noisePos.xyz/5.0, 5, 2.0, 0.5, 1.0);
        float yOff = cfbm(noisePos.yzx/5.0, 5, 2.0, 0.5, 1.0);
        float zOff = cfbm(noisePos.zxy/5.0, 5, 2.0, 0.5, 1.0);

        wind += vec3(xOff, (yOff+0.5)/12.0, zOff)*max(length(wind)/3.0, 15.0)/(height+1.0);
        wind.y += length(wind.xz)/8.0;

        float hourNoise = cfbm((blockNoisePos/100000.0) + (Position / 50.0), 5, 2.0, 0.5, 1.0);
        float hour = VeilRenderTime/3600.0;
        hour += hourNoise;

        pos += getSwayOffset(hour, wind, windRaw, height, realHeight);
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}