#include veil:space_helper

#include pmweather:storms/storms

#include pmweather:math
#include pmweather:noise
#include pmweather:lighting
#include pmweather:colors

#veil:buffer veil:camera VeilCamera

#define MAX_STORMS 16
#define MAX_POINT_LIGHTS 32
#define MAX_SMOKE 64

struct CloudReturn{
    float cloudDensity;
    float rainDensity;
    float dustDensity;
    float brighten;
    float emit;
};

struct Render{
    vec4 col;
    float lightEnergy;
    vec3 pointLightColor;
    float fireEnergy;
    float depth;
};

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D DistantHorizonsDepthSampler;

uniform sampler2D NoiseSampler;
uniform sampler2D NoiseXSampler;
uniform sampler3D Noise3DSampler;

uniform vec4 FogColor;

uniform int shouldRender;

uniform vec2 dhPlanes;
uniform float dhRenderDistance;
uniform mat4 dhProjectionInv;

in vec2 texCoord;

out vec4 fragColor;

uniform float layer0height;
uniform float layerCheight;
uniform float stormSize;
uniform float rain;
uniform float snow;
uniform float fire;
uniform float ash;
uniform float rainStrength;

uniform int stormCount;
uniform vec3 stormPositions[MAX_STORMS];
uniform vec2 stormVelocities[MAX_STORMS];
uniform float stormSmoothStages[MAX_STORMS];
uniform int stormTypes[MAX_STORMS];
uniform float stormOcclusions[MAX_STORMS];
uniform float tornadoWindspeeds[MAX_STORMS];
uniform float tornadoWidths[MAX_STORMS];
uniform float tornadoTouchdownSpeeds[MAX_STORMS];
uniform int visualOnlys[MAX_STORMS];
uniform int stormDyings[MAX_STORMS];
uniform float stormSpins[MAX_STORMS];
uniform float tornadoShapes[MAX_STORMS];
uniform float rainIntensities[MAX_STORMS];

uniform int pointLightCount;
uniform vec4 pointLights[MAX_POINT_LIGHTS];
uniform float pointLightStretches[MAX_POINT_LIGHTS];
uniform vec3 pointLightColors[MAX_POINT_LIGHTS];

uniform int smokeParticleCount;
uniform vec3 smokeParticlePositions[MAX_SMOKE];
uniform float smokeParticleSizes[MAX_SMOKE];
uniform float smokeParticleBrightness[MAX_SMOKE];
uniform float smokeParticleOpacity[MAX_SMOKE];

const int maxSteps = 800;
uniform float downsample;
uniform float time;
uniform float worldTime;
uniform float overcastPerc;
uniform int computationalFBMNoise;

uniform vec3 sunDir;
uniform vec3 lightingColor;
uniform int simpleLighting;

const float renderDistance = 6000.0;

uniform int quality;

float fbm(vec3 x, int octaves, float lacunarity, float gain, float amplitude){
    if(computationalFBMNoise == 1){
        return cfbm(x, getOctavesFromQuality(octaves, quality), lacunarity, gain, amplitude);
    }

    float n = tfbm(x, getOctavesFromQuality(octaves, quality), lacunarity, gain, amplitude, NoiseSampler);

    return n;
}

CloudReturn getClouds(vec3 position, int octaveReduction){
    float totalCloud = 0.0;
    float totalBGCloud = 0.0;
    float totalRain = 0.0;
    float totalDust = 0.0;
    float upperLevel = 0.0;
    float emit = 0.0;

    vec2 scroll = vec2(worldTime);

    vec3 noisePos = position + vec3(scroll.x, -worldTime/2.0, scroll.y);
    vec3 smokeNoisePos = position + (vec3(scroll.x, -worldTime/2.0, scroll.y)/100.0);
    vec3 cloudNoisePos = position + vec3(worldTime*0.5, 0.0, worldTime*0.5);
    vec3 overrideNoisePos = position + vec3(worldTime*0.25, 0.0, worldTime*0.25);

    vec3 noisePos2 = position + vec3(scroll.x*4.0, -worldTime/2.0, scroll.y*4.0);
    vec3 cloudNoisePos2 = position + vec3(worldTime*1.5, 0.0, worldTime*1.5);

    vec3 noisePosIT = position + vec3(scroll.x, worldTime/2.0, scroll.y);

    if(octaveReduction == 0 && quality == 4){
        octaveReduction = -2;
    }

    float noise1 = fbm(noisePos/90.0, 4-octaveReduction, 2.0, 0.5, 1.0);
    float noise2 = fbm(noisePosIT/120.0, 2-octaveReduction, 2.0, 0.3, 1.0);
    float noise3 = noise2d(noisePos.xz/25.0, NoiseXSampler)+(noise1*0.4);
    float sNoise4 = noise3+(noise1*0.2);

    float smokeNoise = -10.0;
    float smokeNoiseLarge = -10.0;
    float smoke = 0.0;
    float smokeBright = 0.0;
    for(int i = 0; i < smokeParticleCount; i++){
        vec3 pos = smokeParticlePositions[i];
        float size = smokeParticleSizes[i];
        float bright = smokeParticleBrightness[i];
        float opacity = smokeParticleOpacity[i];

        float dist = length((position-pos)*vec3(1.0,0.75+(size/100.0),1.0));
        if(dist <= size && opacity > 0.0){
            if(smokeNoise < -9.0){
                smokeNoise = fbm(smokeNoisePos/2.5, 4-octaveReduction, 2.0, 0.5, 1.0);
                smokeNoiseLarge = fbm(smokeNoisePos/20.0, 6-octaveReduction, 2.0, 0.5, 1.0);
            }

            float smNoise = mix(smokeNoise, smokeNoiseLarge, clamp(size/50.0, 0.25, 0.75));

            dist *= 1.0+(((smNoise+1.0)/2.0)*0.8);

            float sm = clamp(dist/size, 0.0, 1.0);
            sm *= sm;
            sm = 1.0-sm;

            sm *= opacity;
            sm *= 1.0-(((smNoise+1.0)/2.0)*0.6);
            sm *= 1.65;

            smoke += sm;
            smokeBright = max(smokeBright, sm*bright);
        }
    }

    totalCloud = max(totalCloud, smoke);
    totalDust = max(totalDust, smoke * 10.0);
    emit = max(emit, smokeBright);

    float bdensityNoise = min(noise2d(cloudNoisePos.xz/400.0, NoiseXSampler), 1.0);
    float boverrideNoise = clamp(noise2d(overrideNoisePos.xz/200.0, NoiseXSampler)+1.0, 0.0, 2.0)/2.0;
    float bcloudNoise = min(noise2d(noisePos.xz/30.0, NoiseXSampler), 1.0);
    float bbaseNoise = noise2d(noisePos.xz/90.0, NoiseXSampler);
    float bheightNoise = noise2d(noisePos.zx/90.0, NoiseXSampler);

    float bgc = 0.0;
    float bv = clamp(bdensityNoise-(1.0-overcastPerc), 0.0, 1.0);
    bgc += max(pow1_4(bv), 0.0);
    float bgCloudBase = mix(mix(0.0, 150.0, clamp((bbaseNoise+1.0)*0.5, 0.0, 1.0)), 0.0, cube(bv))+layer0height;
    float bgCloudHeight = mix(300.0, 850.0, clamp((bheightNoise+1.0)*0.5, 0.0, 1.0));
    bgc *= mix(clamp((bcloudNoise-0.1)+bv, 0.0, 1.0), 1.0, bv);
    bgc = sqrt(bgc)*0.5;
    bgc *= mix(bgCloudHeight/850.0, 1.0, sqrt(bv));

    if(position.y > layer0height && position.y < layer0height+1000.0 && overcastPerc > 0.0){
        float bgClouds = 0.05;
        float detailNoise = fbm(noisePos/90.0, 5-octaveReduction, 2.0, 0.5, 1.0);
        float densityNoise = min(bdensityNoise+(detailNoise*0.1), 1.0);
        float cloudNoise = min(bcloudNoise+(detailNoise*0.3), 1.0);
        float baseNoise = bbaseNoise+(detailNoise*0.1);
        float heightNoise = bheightNoise+(detailNoise*0.1);

        float v = clamp(densityNoise-(1.0-overcastPerc), 0.0, 1.0);
        float vcube = cube(v);
        bgClouds += max(pow1_4(v), 0.0);

        float base = mix(mix(0.0, 150.0, clamp((baseNoise+1.0)*0.5, 0.0, 1.0)), 0.0, vcube)+layer0height;
        float height = mix(300.0, 850.0, clamp((heightNoise+1.0)*0.5, 0.0, 1.0));

        bgClouds *= mix(clamp((cloudNoise-0.1)+square(v), 0.0, 1.0), 1.0, vcube);

        bgClouds *= 1.0 + detailNoise*0.2;

        v = clamp((position.y-base)/height, 0.0, 1.0);
        bgClouds -= square(v);
        bgClouds *= 1.0-clamp((base-position.y)/50.0, 0.0, 1.0);

        bgClouds = pow1_8(bgClouds)*0.5;

        totalBGCloud = max(totalBGCloud, bgClouds);
        upperLevel = max(upperLevel, bgClouds);
    }

    if(position.y > layerCheight && position.y < layerCheight+2000.0 && overcastPerc > 0.0){
        float bgClouds = 0.0;
        float detailNoise = fbm(noisePos2/500.0, 2-octaveReduction, 2.0, 0.5, 1.0);
        float densityNoise = min(noise2d(cloudNoisePos2.xz/800.0, NoiseXSampler)+(detailNoise*0.1), 1.0);
        float warpNoiseX = noise2d(noisePos2.zx/400.0, NoiseXSampler);
        float warpNoiseY = noise2d(noisePos2.xz/400.0, NoiseXSampler);
        float cloudNoise = min(noise2d((noisePos2.xz/200.0) + (vec2(warpNoiseX, warpNoiseY)*100.0), NoiseXSampler)+(detailNoise*0.2), 1.0);
        float baseNoise = noise2d(noisePos2.xz/400.0, NoiseXSampler)+(detailNoise*0.1);
        float heightNoise = noise2d(noisePos2.zx/150.0, NoiseXSampler)+(detailNoise*0.1);

        float v = clamp(densityNoise-(1.0-overcastPerc), 0.0, 1.0);
        float vcube = cube(v);
        bgClouds += max(pow1_4(v), 0.0);

        float base = mix(mix(0.0, 1000.0, clamp((baseNoise+1.0)*0.5, 0.0, 1.0)), 0.0, vcube)+layerCheight;
        float height = mix(200.0, 500.0, clamp((heightNoise+1.0)*0.5, 0.0, 1.0));

        bgClouds *= mix(clamp(sqrt(cloudNoise+0.3)+square(v), 0.0, 1.0), 1.0, vcube);

        bgClouds *= 1.0 + detailNoise*0.2;

        v = clamp((position.y-base)/height, 0.0, 1.0);
        bgClouds -= square(v);
        bgClouds *= 1.0-clamp((base-position.y)/50.0, 0.0, 1.0);

        v = clamp(densityNoise, 0.0, 1.0);
        bgClouds = sqrt(bgClouds)*mix(0.35, 0.0, pow5(v));

        totalBGCloud = max(totalBGCloud, bgClouds*0.7);
    }

    for(int i = 0; i < stormCount; i++){
        float clouds = 0.0;
        float rainam = 0.0;
        vec3 pos = stormPositions[i];
        vec2 vel = stormVelocities[i];
        float smoothStage = stormSmoothStages[i];
        float windspeed = tornadoWindspeeds[i];
        float width = max(tornadoWidths[i], 15.0);
        float touchdownSpeed = tornadoTouchdownSpeeds[i];
        float tornadoShape = tornadoShapes[i];
        float rainIntensity = rainIntensities[i];
        float stormSpin = stormSpins[i];
        int stormType = stormTypes[i];
        float occlusion = stormOcclusions[i];
        bool visualOnly = visualOnlys[i] == 1;
        bool dying = stormDyings[i] == 1;
        bool tornadic = smoothStage >= 3.0;

        StormData stormData = StormData(pos, vel,
                smoothStage,
                windspeed, width,
                touchdownSpeed, tornadoShape,
                rainIntensity, stormSpin,
                stormType, occlusion,
                visualOnly, dying,
                tornadic,
                distance(position, VeilCamera.CameraPosition));

        if(stormType == 3){ // Fire Whirl
            float torPerc = clamp01(windspeed/55.0);
            float fnlTop = pos.y + mix(15.0, 85.0, torPerc);
            float tornadoHeight = pos.y - 1.0;

            float tornado = 1.0;
            float percFnlHeight = boundedPercent01(pos.y, fnlTop, position.y);
            float percCos = getCosPerc(percFnlHeight);

            float wid = (width/5.0) + (20.0*cube(percFnlHeight)*torPerc);
            wid = mix(wid, 0.0, (1.0-percFnlHeight)*(1.0-torPerc));
            float th = 1.0-clamp01((position.y-tornadoHeight)/30.0);
            wid = mix(wid, 0.0, cube(th));
            float maxWid = width;
            vec3 torPos = pos;

            float ropeMod = 3.0;

            float nx = cnoise(vec3(pos.xz/500.0, time/200.0))*5.0*ropeMod;
            float nz = cnoise(vec3(time/200.0, pos.zx/500.0))*5.0*ropeMod;
            vec3 attachmentPoint = vec3(nx, 0.0, nz);

            float xAdd = cnoise(vec3(pos.xz/250.0, (time/200.0)+((position.y*ropeMod)/50.0)))*3.0*ropeMod;
            float zAdd = cnoise(vec3((time/200.0)+((position.y*ropeMod)/50.0), pos.zx/250.0))*3.0*ropeMod;

            float a = pow3_4(percFnlHeight);
            xAdd *= a;
            zAdd *= a;

            torPos += mix(vec3(0.0), vec3(attachmentPoint.x, 0.0, attachmentPoint.z), percCos);
            torPos += vec3(xAdd, 0.0, zAdd);

            float torDist = distance(torPos.xz, position.xz);
            vec3 localTorPos = position-torPos;

            float widPerc = 1.0-clamp01(torDist/wid);
            float widMaxPerc = clamp01(wid/maxWid);
            float rotation = -stormSpin*3.0;
            float rotation2 = -stormSpin/1.5;

            mat2 torSpin = spin(rotation+(torDist/50.0));
            mat2 torSpin2 = spin(rotation2+(torDist/150.0));
            vec3 torSpinPos = vec3(torSpin*localTorPos.xz, position.y-(time/2.0));
            vec3 torSpinPos2 = vec3(torSpin2*localTorPos.xz, position.y-(time/2.0));

            float nComp1 = fbm(torSpinPos/20.0, 3-octaveReduction, 2.0, 0.5, 1.0);
            float nComp2 = fbm(torSpinPos2/40.0, 3-octaveReduction, 2.0, 0.5, 1.0);

            float torNoise1 = mix(nComp1, nComp2, sqrt(widMaxPerc));

            wid *= mix(0.8 + (torNoise1*0.2), 0.9, clamp01(width/1000.0)*0.9);

            widPerc = 1.0-clamp01(torDist/wid);

            tornado *= widPerc;
            tornado = pow(tornado, 1.5)*4.0;
            tornado *= clamp01((position.y-tornadoHeight)/20.0);
            tornado *= 0.8 + (torNoise1*0.2);

            float heightFadeAt = clamp(windspeed * 2.0, 0.0, 85.0);
            tornado *= sqrt(1.0-clamp01(localTorPos.y/heightFadeAt));
            tornado *= 1.0-((1.0-clamp01(localTorPos.y/10.0))*0.3);

            clouds = max(clouds, tornado);
            totalDust = max(totalDust, tornado * 10.0);
            emit = max(emit, pow(1.0-clamp01(localTorPos.y/50.0), 1.5) * 0.35 * pow1_4(tornado));
        }else if(stormType == 2){ // Cyclone
            float hHeight = layer0height+1000.0;
            float heightP = boundedPercent(layer0height, hHeight, position.y);
            float heightPSqr = square(heightP);
            float sze = width;
            pos += vec3(heightPSqr*sze*0.1, 0.0, -heightPSqr*sze*0.05);
            float dist = distance(position.xz, pos.xz);

            sze *= 1.0+(heightPSqr*0.4*(1.0+clamp01(dist/sze)));

            float eyeSize = 0.2;
            float eyeCutSize = 0.2 + (heightP*0.2);

            if(dist > sze*1.2 || position.y > hHeight){
                continue;
            }

            dist *= 1.0+(((noise3+1.0)/2.0)*0.2);

            float intensity = pow3_4(clamp01(windspeed/65.0));

            vec2 relPos = pos.xz-position.xz;

            float d = sze/(3.0+(windspeed/12.0));
            float d2 = sze/(1.15+(windspeed/12.0));
            float dE = (sze*0.3)/(1.75+(windspeed/12.0));

            float fac = 1.0+(max((dist-(sze*(eyeSize*2.0)))/sze, 0.0)*2.0);
            d *= fac;
            d2 *= fac;

            float angle = ((atan2(relPos.y, relPos.x)-(dist/d)));
            float angle2 = ((atan2(relPos.y, relPos.x)-(dist/d2)));
            float angleE = ((atan2(relPos.y, relPos.x)-(dist/dE)));

            float weak = 0.0;
            float strong = 0.0;
            float intense = 0.0;

            float staticBands = sin(angle-(PI/2.0));
            staticBands *= pow1_8(clamp01(dist/(sze*0.25)));
            staticBands *= 1.25*pow3_4(intensity);
            if(staticBands < 0){
                weak += abs(staticBands);
            }else{
                weak += abs(staticBands) * sqrt(1.0-clamp01(dist/(sze*0.65)));
                weak *= clamp01((windspeed-70.0)/40.0);
            }

            float rotatingBands = sin((angle2+radians(worldTime/8.0))*6.0);
            rotatingBands *= pow1_8(clamp01(dist/(sze*0.25)));
            rotatingBands *= 1.25*pow3_4(intensity);
            strong += mix((abs(rotatingBands)*0.3)+0.7, weak, 0.45);
            intense += mix((abs(rotatingBands)*0.2)+0.8, weak, 0.3);
            weak = ((abs(rotatingBands)*0.3)+0.6)*weak;

            float localCloud = 0.0;

            localCloud += mix(mix(weak, strong, clamp01((windspeed-40.0)/90.0)), intense, clamp01((windspeed-120.0)/60.0));

            float eye = sin((angleE+radians(worldTime/4.0))*2.0);
            eye = mix(eye, 1.0, 1.0-clamp01(dist/(sze*eyeSize)));
            localCloud = max(sqrt(1.0-clamp01(dist/(sze*eyeSize*2.0)))*(abs(eye*0.3)+0.7)*1.4*intensity, localCloud);

            localCloud *= sqrt(1.0-clamp01(dist/sze));
            localCloud *= mix(1.0, square(clamp01(dist/(sze*eyeCutSize))), 0.5+(clamp01((windspeed-65.0)/60.0)*0.5));

            float noiseMain = (1.0+noise1)/2.0;
            localCloud *= mix(0.8 + noiseMain*0.4, 1.0, clamp01((windspeed-75.0)/50.0));
            localCloud *= 0.8 + noiseMain*0.4;

            float eyeBG = mix(1.0, square(clamp01(dist/(sze*eyeCutSize))), clamp01((windspeed-65.0)/60.0));
            totalBGCloud *= eyeBG;
            bgc *= eyeBG;

            if(localCloud > 0.7){
                float dif = (localCloud-0.7)/3.0;
                localCloud -= dif;
            }

            if(heightP >= 0.0 && heightP <= 1.0){
                clouds += localCloud-(0.4*(1.0-square((1.4*heightP)-0.4)));
                clouds *= sqrt(1.0-heightP);
            }

            if(heightP <= 1.0){
                float rain = max(localCloud-0.15, 0.0)*2.0;
                rain *= pow1_4(1.0-clamp01(dist/width));
                rain *= 1.0 + noise2*0.2;
                rain *= sqrt(1.0-heightP);
                rainam += rain*rainStrength;
            }

            clouds = sqrt(clouds)*0.8;
        }else if(stormType == 1){ // Squall
            smoothStage = min(smoothStage, 3.0);

            float subcellular = clamp01(smoothStage);
            float cellular = clamp01(smoothStage-0.5);

            float stormHeight = mix(400.0, 1600.0, cellular);
            float baseHeight = layer0height;

            float v = clamp01((position.y-layer0height)/1600.0);
            v *= v;
            vec2 right = normalize(vel.yx*vec2(1,-1));
            vec2 fwd = normalize(vel.xy);
            vec3 fwd3 = vec3(fwd.x, 0.0, fwd.y);
            vec3 right3 = vec3(right.x, 0.0, right.y);

            float rawDist = distance(position.xz, pos.xz);

            vec3 l = right3*-(stormSize*5);
            vec3 r = right3*(stormSize*5);

            vec3 offset = -fwd3*square(clamp01(rawDist/(stormSize*5.0)))*(stormSize*1.5);
            l += offset;
            r += offset;

            l += vec3(2000.0*v, 0.0, -900.0*v);
            r += vec3(2000.0*v, 0.0, -900.0*v);

            l += pos;
            r += pos;

            float dist = minimumDistance(l.xz, r.xz, position.xz);

            float sze = stormSize*10.0;

            if(position.y > layer0height+1000.0){
                sze *= 8.0;
            }

            if(dist > sze){
                continue;
            }

            baseHeight += noise1*15.0;

            float noiseMain = noise1;

            v = clamp01((position.y-layer0height)/1600.0);
            noiseMain = mix(noiseMain, noise3+(noise1*0.25), cellular*v);

            float size = stormSize*1.5;
            stormHeight *= 1.0 + noiseMain*0.1;

            offset = fwd3*stormSize*0.5;
            l += offset;
            r += offset;
            vec2 nearPoint = nearestPoint(l.xz, r.xz, position.xz);
            vec2 facing = position.xz-nearPoint;
            float behind = -dot(facing, fwd);
            behind += noise3*stormSize*0.2;

            float heightFromBase = position.y-baseHeight;
            float heightPerc = clamp01(heightFromBase/stormHeight);
            float currentHeight = heightPerc*stormHeight;
            float heightFromTop = stormHeight-currentHeight;

            v = clamp01(currentHeight/max(stormHeight, 1600.0));
            v *= v;
            pos += vec3(2000.0*v, 0.0, -900.0*v);

            size = mix(size, mix(mix(size, size/2.0, cellular), size, sqrt(1.0-clamp01(currentHeight/300.0))), pow1_4(heightPerc));

            v = 1.0-clamp01(heightFromTop/1600.0);
            size = mix(size, mix(size, size*8.0, cellular), pow5(v));
            size = mix(size, size/4.0, clamp01(1.0-smoothStage));

            size = mix(size, size*4.0, clamp01(behind/(stormSize/4))*clamp01(smoothStage-1.0));

            size *= 1.0 + noiseMain*0.4;

            float distPerc = clamp01(dist/size);

            if(distPerc >= 0.999){
                continue;
            }

            if(position.y < baseHeight){
                float clearing = 1.0-cube(distPerc);
                clearing *= subcellular;
                totalBGCloud *= 1.0-clearing;
            }

            float distBased = 1.0-distPerc;
            float cloudField = (noise3-clamp01(1.0-(smoothStage-1.0)))*clamp01(heightFromTop/65.0)*pow1_4(1.0-distPerc);
            cloudField *= clamp01(rawDist/mix(stormSize, stormSize*5.0, clamp01(smoothStage/1.25)));

            float baseField = mix(cloudField, distBased, clamp01(smoothStage/2.0));
            clouds += mix(0.0, baseField, step(baseHeight, position.y)*step(position.y, baseHeight+stormHeight));

            clouds *= clamp01(heightFromTop/mix(100.0, 300.0, clamp01(smoothStage*0.8)));
            clouds *= clamp01(heightFromBase/25.0);

            float rain = step(position.y, baseHeight);

            float shelfBehind = behind+20.0;
            if(shelfBehind > 0.0){
                float baseHeightMod = baseHeight * (1.0 - (pow4(clamp01(1.0-((shelfBehind-20.0)/(stormSize/2.0))))*0.45*clamp01(smoothStage-1.0)));

                float heightFromBaseMod = position.y-baseHeightMod;

                float shelf = baseField;
                shelf *= clamp01(heightFromBaseMod/25.0);
                shelf *= 1.0-clamp01((heightFromBaseMod-20.0)/40.0);
                shelf *= square(clamp01(shelfBehind/50.0));

                rain *= 1.0-clamp01(heightFromBaseMod/15.0);

                clouds = max(shelf, clouds);
            }

            size = stormSize*0.75*clamp01(smoothStage);
            size = mix(size, size*8.0, clamp01(behind/(stormSize/4.0))*clamp01(smoothStage-1.0));
            size *= 1.0 + noise2*0.4;
            distPerc = clamp01(dist/size);

            clouds = sqrt(clouds)*0.8;

            rain *= 1.0-pow(distPerc, 1.35);
            rain *= 1.0 + noise2*0.2;
            rain *= clamp01(smoothStage-0.5);

            if(behind > 0.0){
                rain = pow(rain, 2.5);
            }

            behind -= stormSize/3.0;

            if(behind < 0.0){
                rain *= 1.0-clamp01(abs(behind)/(stormSize/3));
            }

            rainam += max(rain, 0.0)*rainStrength*rainIntensity;
        }else if(stormType == 0){ // Supercell/Tornado
            SupercellValues values = SupercellValues(stormSize, layer0height, time, 0.0, quality, octaveReduction);
            bool inBounds = inSupercellBounds(position, stormData, values);

            values.noise4 = sNoise4 < -9.0 ? 0.0 : sNoise4;
            vec3 samplePos = position;

            float hv = clamp01(position.y/1600.0);
            samplePos.y += noise1*mix(15.0, 60.0, hv);

            float noiseMain = mix(noise1, sNoise4, hv);
            values.stormSize *= 1.0 + (noiseMain*mix(0.2, 0.5, hv));

            StormReturn rtrn = getSupercell(samplePos, stormData, values);

            float dist = distance(samplePos.xz, pos.xz);
            float clearing = clamp01(dist/(stormSize*mix(1.0, 4.0, clamp01(smoothStage/1.75))));
            clearing = mix(1.0, clearing, clamp01(smoothStage/1.75));
            clearing = square(clearing)*0.6;
            clearing += 0.4;
            totalBGCloud *= clearing;
            bgc *= clearing;

            clouds += max(rtrn.clouds, 0.0);
            smoothStage = min(smoothStage, 3.0);

            if(rtrn.clouds < 0.005){
                float dist = distance(position.xz, pos.xz);

                float baseHeight = values.layer0height+10.0;//-20.0;
                baseHeight += noise1*15.0;

                vec3 localPos = position-pos;
                mat2 speen = spin((-time/(20.0*50.0))+(dist/(stormSize*2.0)));
                mat2 speen2 = spin((-time/(20.0*15.0))+(dist/(stormSize/2.0)));

                vec3 spinNoisePos = vec3(speen*localPos.xz, position.y-time);
                vec3 spinNoisePos2 = vec3(speen2*localPos.xz, position.y-time);

                float spinNoise1 = 0.0;//fbm(spinNoisePos/120.0, 5-octaveReduction, 2.0, 0.5, 1.0);
                float spinNoise2 = 0.0;//fbm(spinNoisePos2/80.0, 5-octaveReduction, 2.0, 0.5, 1.0);

                float heightFromBase = position.y-baseHeight;

                float size = (stormSize*0.35)+(width/5.0);
                float maxSize = size * 1.4;

                if(smoothStage > 1.75 && position.y > pos.y-50.0 && dist < maxSize && position.y < layer0height+100.0){
                    spinNoise2 = fbm(spinNoisePos2/60.0, 4-octaveReduction, 4.0, 0.5, 1.0);
                }

                float noiseMain = spinNoise2;

                size *= 1.0 + (noiseMain*0.4);
                size = min(maxSize, size);

                float distPerc = clamp01(dist/size);

                float wallcloudLower = 120.0*pow1_4(1.0-distPerc)*clamp01((smoothStage-1.75)*3.0);
                float wallcloudNoiseMod = clamp01((spinNoise2+1.0)*0.5);
                wallcloudLower *= mix(0.5, 1.75, square(wallcloudNoiseMod))*1.25;
                float wallcloudBaseHeight = baseHeight-wallcloudLower;
                float wallcloud = mix(0.0, 1.0-distPerc, step(position.y, baseHeight)*sqrt(smoothstep(wallcloudBaseHeight, wallcloudBaseHeight+60.0, position.y)));
                wallcloud *= clamp01((smoothStage-2.0)*5.0);
                clouds += wallcloud;

                float fnlTop = max(baseHeight-wallcloudLower, pos.y+30.0);
                float torPercDampen = square(clamp01(dist/(width/1.25)))*touchdownSpeed*0.5;
                float torPerc = clamp01((windspeed-torPercDampen)/touchdownSpeed);
                float tornadoHeight = mix(fnlTop, pos.y-50.0, mix(wallcloudNoiseMod * 0.3, 1.0, torPerc));

                if(tornadic && position.y < baseHeight-wallcloudLower && position.y > pos.y-50.0 && dist < max(width*4.5, stormSize/3.0)){
                    float tornado = clamp01(torPerc/0.15);
                    float percFnlHeight = clamp01((position.y-pos.y)/(fnlTop-pos.y));
                    float percCos = getCosPerc(percFnlHeight);

                    float torShape = mix(tornadoShape, 20.0, pow(clamp01(width/500.0), 1.75));

                    float wid = (width/2.5) + ((width/2.5)*percFnlHeight*torPerc) + ((stormSize/mix(torShape+2.0, torShape, torPerc)) * pow4(percFnlHeight));
                    wid = mix(wid, 0.0, (1.0-percFnlHeight)*(1.0-torPerc));
                    float th = 1.0-clamp01((position.y-tornadoHeight)/30.0);
                    wid = mix(wid, 0.0, cube(th));

                    wid *= mix((wallcloudNoiseMod*0.25)+0.75, (wallcloudNoiseMod*0.5)+0.5, percFnlHeight);
                    float maxWid = (width/4.0) + ((width/4.0)*torPerc) + ((stormSize/8.0) * torPerc);
                    vec3 torPos = pos;

                    float ropeMod = mix(3.0, 1.0, clamp01(width/30.0));
                    ropeMod = mix(ropeMod, 1.0, clamp01((windspeed-65.0)/30.0));
                    ropeMod = mix(0.1, ropeMod, clamp01((windspeed/touchdownSpeed)*1.35));

                    float nx = noise(vec3(pos.xz/50.0, time/200.0), NoiseSampler)*40.0*ropeMod;
                    float nz = noise(vec3(time/200.0, pos.zx/50.0), NoiseSampler)*40.0*ropeMod;
                    vec3 attachmentPoint = vec3(nx, 0.0, nz);

                    float xAdd = noise(vec3(pos.xz/25.0, (time/100.0)+((position.y*ropeMod)/25.0)), NoiseSampler)*25.0*ropeMod;
                    float zAdd = noise(vec3((time/100.0)+((position.y*ropeMod)/25.0), pos.zx/25.0), NoiseSampler)*25.0*ropeMod;

                    float a = pow3_4(percFnlHeight);
                    xAdd *= a;
                    zAdd *= a;

                    torPos += mix(vec3(0.0), vec3(attachmentPoint.x, 0.0, attachmentPoint.z), percCos);
                    torPos += vec3(xAdd, 0.0, zAdd);

                    float torDist = distance(torPos.xz, position.xz);
                    vec3 localTorPos = position-torPos;

                    float widPerc = 1.0-clamp01(torDist/wid);
                    float widMaxPerc = clamp01(wid/maxWid);
                    float rotation = -stormSpin*3.0;
                    float rotation2 = -stormSpin/1.5;

                    mat2 torSpin = spin(rotation+(torDist/50.0));
                    mat2 torSpin2 = spin(rotation2+(torDist/150.0));
                    mat2 torSpin3 = spin((rotation2*2.0)+(torDist/165.0));
                    vec3 torSpinPos = vec3(torSpin*localTorPos.xz, position.y-(time/2.0));
                    vec3 torSpinPos2 = vec3(torSpin2*localTorPos.xz, position.y-(time/2.0));
                    vec3 torSpinPos3 = vec3(torSpin3*localTorPos.xz, position.y-(time/4.0));

                    float nComp1 = fbm(torSpinPos/20.0, 2-octaveReduction, 2.0, 0.5, 1.0);
                    float nComp2 = fbm(torSpinPos2/40.0, 3-octaveReduction, 2.0, 0.5, 1.0);

                    float torNoise1 = mix(nComp1, nComp2, sqrt(widMaxPerc));

                    wid *= mix(0.8 + (torNoise1*0.2), 0.9, clamp01(width/1000)*0.9);

                    widPerc = 1.0-clamp01(torDist/wid);

                    tornado *= widPerc;
                    tornado = pow(tornado, 1.5)*4.0;
                    tornado *= clamp01((position.y-tornadoHeight)/20.0);
                    tornado *= 0.8 + (torNoise1*0.2);

                    float dust = 1.0;
                    float dcNoise1 = 0.0;

                    if(position.y < pos.y+20.0+(width/7.5)+25.0 && width < 250.0){
                        dcNoise1 = sqrt((fbm(torSpinPos3/40.0, 4-octaveReduction, 5.0, 0.5, 8.0) * 0.5) + 0.75) * 0.45;
                    }

                    float dcPerc = clamp01((windspeed-45.0)/30.0);
                    float h = 5.0 + (square(dcNoise1)*((width/7.5)+25.0));
                    float dcTop = pos.y+(max(dcPerc, 0.35)*h);

                    if(position.y < dcTop && width < 250.0){
                        float percDCHeight = clamp01((position.y-(pos.y-10.0))/(dcTop-pos.y));

                        wid = ((width/2.5) + ((width/2.5)*percFnlHeight*torPerc) + (25.0*(width/200.0))) + (15.0*pow(percDCHeight, 1.5)*pow1_4(dcPerc));
                        wid *= mix(1.0, 1.25, dcNoise1);
                        wid *= mix(0.6 + (dcNoise1*0.5), 0.85, clamp01(width/500)*0.9);
                        wid *= 0.75;
                        widPerc = 1.0-clamp01(torDist/wid);
                        widPerc = pow1_4(widPerc);
                        float v = clamp01(torDist/(wid*0.9));
                        widPerc *= cube(v);
                        dust *= widPerc;
                        dust = pow(dust, 1.5)*0.15;
                        dust *= clamp01((dcTop-position.y)/20);
                        dust *= clamp01((position.y-(pos.y-20))/20);
                        dust *= 0.8 + (dcNoise1*0.2);
                        dust *= dcPerc;
                        dust *= 1.0-clamp01((width-50.0)/200.0);
                        dust = sqrt(dust) * 0.85;
                        tornado = max(tornado, dust);
                        totalDust = max(totalDust, dust);
                    }

                    clouds = max(clouds, tornado);
                }
            }

            clouds = sqrt(clouds)*0.8;

            float rain = max(rtrn.precip, 0.0);
            float rainYDif = (rtrn.rainY+25.0)-position.y;
            rain *= clamp01(rainYDif/35.0);

            rain *= 1.0 + noise2;
            rainam += rain*rainStrength;
        }

        totalCloud = max(totalCloud, clouds);
        totalRain = max(totalRain, rainam);
    }

    float bgcRain = boverrideNoise*bgc;

    if(position.y <= bgCloudBase && bgcRain > 0.15){
        float rain = (bgcRain-0.15)*1.65;
        rain *= 1.0 + noise2*0.2;
        totalRain = max(totalRain, rain*rainStrength*1.35);
    }

    float cdensity = max(max(totalCloud, totalBGCloud), 0.0);
    float eV = pow1_4(clamp01(cdensity));

    return CloudReturn(cdensity, max(totalRain, 0.0), max(totalDust, 0.0), max(upperLevel, 0.0), clamp01(emit*eV));
}

float lightmarch(vec3 ro, vec3 dir, int steps, float marchSize){
    float totalDensity = 0.0;

    marchSize *= 0.75;

    float d0 = 0.0;
    for(int i = 0; i < steps; i++){
        d0 += marchSize;
        marchSize += marchSize/4.0;

        vec3 p = ro+(dir*d0);

        CloudReturn d = getClouds(p, 8);
        totalDensity += d.cloudDensity*0.015*marchSize;

        if(BeersLaw(totalDensity, 0.9) < 0.01){
            break;
        }
    }

    return BeersLaw(totalDensity, 0.9);
}

Render render(vec2 uv, float maxDepth){
    float light = clamp((sunDir.y+0.25)/0.3, 0.0, 1.0);
    vec3 ro = VeilCamera.CameraPosition;
    vec3 rdr = viewDirFromUv(uv);
    float offset = (cnoise(vec3(uv*2048.0, 0.0))*2.0) - 1.0;
    float density = 0.0;
    float totalRain = 0.0;
    float rainDampen = 0.0;

    vec4 res = vec4(0.0);

    float d0 = 1.0;//nearPlane;

    float ms = float(maxSteps);

    bool inCloud = false;
    int cyclesNotInCloud = 0;
    int count = 0;

    float totalTransmittance = 1.0;
    float lightEnergy = 0.0;
    vec3 pointLightColor = vec3(0.0);
    float fireEnergy = 0.0;

    float phase = HenyeyGreenstein(0.3, dot(rdr, sunDir));
    float moonLight = 0.0;
    float moonPhase = 0.0;

    float sunRisePerc = min(sunDir.y + 0.25, 1.0);
    float nSRP = clamp01(sunRisePerc);

    vec3 skycol = mix(vec3(0.015), mix(FogColor.rgb*0.9, skyColor,  0.45), sqrt(nSRP));
    skycol = mix(sunsetSecondary, skycol, clamp01(nSRP*10.0));

    float depth = maxDepth;

    if(sunDir.y < 0.1){
        moonPhase = HenyeyGreenstein(0.3, dot(rdr, sunDir*-1.0));
        moonLight = clamp01((sunDir.y-0.1)/-0.1)*0.25;
        moonPhase = clamp01(cube(moonPhase)*4.0);
    }

    for(int i = 0; i < ms; i++){
        vec3 p = ro+(rdr*d0);

        float rad = 75000.0;

        float dPerc = min(d0/rad, 1.0);
        float offsetY = 1.0-sqrt(1.0-square(dPerc));
        vec3 modP = p + vec3(0.0, offsetY*rad, 0.0);

        float fogReduction = snow+(ash/150.0)+rain;
        fogReduction *= 1.0-clamp01(p.y/1000.0);

        if(d0 >= maxDepth || modP.y > max(layerCheight+1000.0, layer0height+2000.0) || modP.y < -64.0){
            break;
        }

        float v = (count+5.0)/60.0;

        float stepMult = cube(v);
        float multiplier = 1.4;

        stepMult /= 2.0;

        if(quality == 0){
            stepMult *= 5.0;
        }else if(quality == 1){
            stepMult *= 4.0;
        }else if(quality == 2){
            stepMult *= 2.0;
        }else if(quality == 4){
            stepMult /= 2.0;
        }

        if(!inCloud){
            stepMult *= 1.5;
        }

        float smult = 4.0;
        float s = max(((8.0+offset)*smult*stepMult), 0.5);

        float rDist = renderDistance*2.0;

        if(d0 < min(rDist, rad*mix(1.0, 5.0, clamp(p.y/layerCheight, 0.0, 1.0)))){
            float fog = snow+(ash/150.0);

            CloudReturn clouds = CloudReturn(0.0, 0.0, 0.0, 0.0, 0.0);
            CloudReturn realClouds = getClouds(modP, int(clamp01(d0/2048.0)*4.0));

            float fogE = clamp(fog*10.0, 0.0, 1.0);

            clouds.cloudDensity = mix(realClouds.cloudDensity, clouds.cloudDensity, fogE);
            clouds.rainDensity = mix(realClouds.rainDensity, clouds.rainDensity, fogE);
            clouds.dustDensity = mix(realClouds.dustDensity, clouds.dustDensity, fogE);
            clouds.brighten = mix(realClouds.brighten, clouds.brighten, fogE);
            clouds.emit = realClouds.emit * ((clamp(d0/32.0, 0.0, 1.0)*0.75)+0.25) * (1.0 - (light*0.75));

            clouds.cloudDensity *= multiplier;

            float sf = (fog*1.5*clamp(1.0-(d0/256.0), 0.0, 1.0));
            float d = clouds.cloudDensity+sf;
            float r = clouds.rainDensity;

            d *= clamp01(d0/25.0);
            r *= clamp01(d0/35.0);

            d *= 1.0-clamp01((d0-(rDist-3000.0))/3000.0);
            r *= 1.0-clamp01((d0-(rDist-3000.0))/3000.0);

            float rd = d+(r*0.25*(s/8.0));

            count += 1;

            if(rd > 0.0){
                if(d0 < depth){
                    depth = d0;
                }

                cyclesNotInCloud = 0;

                if(!inCloud && d > 0.0){
                    inCloud = true;
                    d0 -= s;
                    count -= 1;
                    continue;
                }

                density += rd*step(0.1, rd);
                totalRain += r;

                if(density > 5.0){
                    rd = max(rd, 2.0);
                    if(r > d){
                        r = 1.0;
                    }
                }

                if(d > 0.0 && totalRain < 0.01){
                    rainDampen += sqrt(d)*1.25;
                }

                float transmittance = mix(1.0, max(light, moonLight)*0.3, clamp(rd*4, 0.0, 1.0));

                if(light > 0.0 && d-sf > 0.0 && totalTransmittance > 0.015 && simpleLighting == 0){
                    int steps = 0;

                    switch(quality){
                        case 0:
                            steps = 2;
                            break;

                        case 1:
                            steps = 3;
                            break;

                        case 2:
                            steps = 4;
                            break;

                        case 3:
                            steps = 6;
                            break;

                        case 4:
                            steps = 10;
                            break;

                        default:
                            steps = 3;
                            break;
                    }

                    transmittance = lightmarch(modP, sunDir, steps, (((10.0-float(steps))/10.0)*20.0)+4.0);
                }

                if(moonLight > 0.0 && light <= 0.0 && d-sf > 0.0 && totalTransmittance > 0.015 && simpleLighting == 0){
                    int steps = 0;

                    switch(quality){
                        case 0:
                            steps = 2;
                            break;

                        case 1:
                            steps = 3;
                            break;

                        case 2:
                            steps = 4;
                            break;

                        case 3:
                            steps = 6;
                            break;

                        case 4:
                            steps = 10;
                            break;

                        default:
                            steps = 3;
                            break;
                    }

                    transmittance = lightmarch(modP, sunDir*-1.0, steps, (((10.0-float(steps))/10.0)*20.0)+4.0);
                }

                if(pointLightCount > 0){
                    for(int j = 0; j < pointLightCount; j++){
                        vec4 posSize = pointLights[j];
                        vec3 pos = posSize.xyz;
                        float size = posSize.w;

                        vec3 color = pointLightColors[j];
                        vec3 stretch = vec3(1.0, pointLightStretches[j], 1.0);

                        float yDist = modP.y-pos.y;
                        float xzDist = distance(modP.xz, pos.xz);

                        float yEffect = clamp01((stretch.y-0.1)*2.0);
                        float sizeM = size;
                        sizeM *= sqrt(max(yDist/25.0, 0.0))+0.25;

                        size = mix(size, sizeM, yEffect);

                        float dist = distance(pos*stretch, modP*stretch);
                        float fac = size/50.0;
                        float invSqr = fac/(dist+fac);
                        float invSqrM = invSqr;

                        invSqrM += 150.0*pow4(invSqrM);
                        invSqrM = mix(invSqrM, invSqr, square(clamp01(yDist/100.0)));

                        invSqr = mix(invSqr, invSqrM, yEffect);

                        float p = invSqr * min(mix(rd, cube(rd/4.0), clamp01(totalTransmittance)), 1.0);
                        float intensity = p * length(color);

                        pointLightColor += max(pow1_8(totalTransmittance), 0.05) * p * color * 0.5;

                        totalTransmittance = clamp01(totalTransmittance + (intensity*0.05));
                    }
                }

                fireEnergy += totalTransmittance * clouds.emit;
                transmittance = max(transmittance, clouds.emit);

                float d3_4 = pow3_4(d);

                float luminance = 1.25 * (d3_4*(clamp(s, 10.0, 35.0)/35.0)) * max(phase, moonPhase);

                float rPerc = clamp(r, 0.0, 1.0);

                vec3 cC = mix(mix(cloudColor, rainColor, clamp(rain, 0.0, 1.0)), rainColor, rPerc);
                float dustP = clamp(pow1_4(clouds.dustDensity), 0.0, 1.0);
                vec3 dC = mix(dustColor*2.5, dustColor, dustP);

                cC = mix(cC, skycol*0.5, 0.65);
                cC = mix(cC, dC, clamp(pow1_8(clouds.dustDensity), 0.0, 1.0));

                if(clouds.dustDensity <= 0.0){
                    cC = mix(lightingColor*max(light, moonLight), cC, clamp(d3_4+(r*5.0)+snow, 0, 1));
                }

                cC = mix(cC, mix(ashColor, fireAshColor, clamp(fire/2.0, 0.0, 1.0)), clamp(ash/1.5, 0.0, 1.0));
                vec4 col = vec4(mix(cC, mix(denseCloudColor, rainColor, rPerc), clamp(rd, 0.0, 1.0))*light, clamp(rd, 0.0, 1.0));

                col.rgb = mix(col.rgb, skycol*0.8, square(clamp01(d0/8000.0))*light);

                col.rgb *= pow(col.a, 1.1);

                res += col * (1.0-res.a);

                lightEnergy += totalTransmittance * luminance;
                totalTransmittance *= transmittance;

                if(totalTransmittance < 0.1){
                    totalTransmittance = 0.0;
                }
            }else{
                if(inCloud){
                    cyclesNotInCloud += 1;
                }

                if(cyclesNotInCloud >= 10){
                    inCloud = false;
                    cyclesNotInCloud = 0;
                }
            }

            if(density > 4.0 || rd > 1.0){
                break;
            }
        }

        d0 += s;
    }

    totalRain -= rainDampen;

    float rv = clamp(1.0-rain, 0.0, 1.0);
    return Render(res, clamp(lightEnergy, mix(0.3, 0.1, clamp(max(rain*4.0, totalRain*0.15), 0.0, 1.0)), clamp(density, 0.0, 1.0))*max(light, moonLight)*clamp(density, 0.0, 1.0)*square(rv), pointLightColor, fireEnergy, depth);
}

void main(){
    if(shouldRender == 0) return;

    vec2 uv = texCoord*downsample;
    if(uv.x > 1.0 || uv.y > 1.0){
        discard;
    }

    float maxTotalRenderDist = max(renderDistance, dhRenderDistance);
    float sceneDistance = maxTotalRenderDist;
    float depth = texture(DiffuseDepthSampler, uv).r;
    vec3 pos = screenToLocalSpace(uv, depth).xyz;

    if(depth < 1.0){
        sceneDistance = length(pos);
    }

    if(dhRenderDistance > 1.0){
        float dhDepth = texture(DistantHorizonsDepthSampler, uv).r;
        if(dhDepth < 1.0){
            sceneDistance = min(sceneDistance, getDistFromDepth(dhDepth, uv, dhProjectionInv, VeilCamera.ViewMat));
        }else if(depth >= 1.0){
            sceneDistance = maxTotalRenderDist*4.0;
        }
    }else if(depth >= 1.0){
        sceneDistance = maxTotalRenderDist*4.0;
    }

    sceneDistance = min(sceneDistance, maxTotalRenderDist*4.0);

    Render renderOut = render(uv, sceneDistance);

    vec3 pointLightColor = renderOut.pointLightColor;
    float pointIntensity = length(pointLightColor);
    if(pointIntensity > 0.8){
        float sub = pointIntensity-0.8;
        float len = pointIntensity;
        len -= sub/1.5;
        pointLightColor = normalize(pointLightColor)*len;
    }

    vec4 col = mix(mix(renderOut.col, vec4(lightingColor, renderOut.col.a), renderOut.lightEnergy*renderOut.col.a), vec4(fireColor, renderOut.col.a), renderOut.fireEnergy);
    col.rgb += pointLightColor*clamp01(pointIntensity*1.5);

    fragColor = vec4(pow(col.rgb, vec3(1.25)), col.a);
}