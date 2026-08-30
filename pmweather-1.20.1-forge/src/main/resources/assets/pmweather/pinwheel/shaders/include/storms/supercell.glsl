#define CELL_HEIGHT 2500.0
#define F_DOMAIN 65000.0
#define F_DOMAIN_Y 65000.0
#define OVERSHOOTING_MULT 1.05
#define NEXT_UPDRAFT_SAMPLE 0.05
#define BASE_PASS_MULT 1.05

#define STOP return StormReturn(-100.0, 0.0, 0.0, vec3(0.0));

#include pmweather:math
#include pmweather:directions
#include pmweather:noise
#include pmweather:motion

struct SupercellValues{
    float stormSize;
    float layer0height;
    float time;
    float noise4;
    int quality;
    int octaveReduction;
};

bool inSupercellBounds(vec3 rawPos, StormData storm, SupercellValues values){
    float size = values.stormSize*20.0;

    float lcl = values.layer0height;
    float el = CELL_HEIGHT + lcl;

    vec3 relPos = rawPos-(storm.position*vec3(1.0, 0.0, 1.0));
    vec3 percentPos = relPos / vec3(size, el*OVERSHOOTING_MULT, size);
    percentPos -= fromCardinal(0.005, 0.015);

    return !(percentPos.x < -0.2 || percentPos.x > 0.8 || rawPos.y < -70.0 || percentPos.y > 1.0 || percentPos.z < -0.8 || percentPos.z > 0.25);
}

StormReturn getSupercell(vec3 rawPos, StormData storm, SupercellValues values){
    if(storm.stormType != 0){
        STOP
    }

    float clouds = 0.0;
    float precip = 0.0;
    float rainY = -100.0;
    vec3 motion = vec3(0.0);

    float smoothStage = min(storm.smoothStage, 3.0);
    float stormStrength = clamp01(smoothStage/1.75);
    float size = values.stormSize*20.0;

    float lcl = values.layer0height;
    float el = CELL_HEIGHT + lcl;

    float disorg = 1.0;

    vec3 rayPos = rawPos;

    float noiseMain = 0.0;
    float noiseLarge = 0.0;
    float noise2D = 0.0;

    vec3 relPos = rayPos-(storm.position*vec3(1.0, 0.0, 1.0));
    vec3 percentPos = relPos / vec3(size, el*OVERSHOOTING_MULT, size);
    percentPos -= fromCardinal(0.005, 0.015);

    if(percentPos.x < -0.2 || percentPos.x > 0.8 || rayPos.y < -70.0 || percentPos.y > 1.0 || percentPos.z < -0.8 || percentPos.z > 0.25){
        STOP
    }

    float fel = F_DOMAIN_Y;
    float flcl = (lcl/el)*fel;
    vec3 fLocalPos = percentPos * vec3(F_DOMAIN, fel*OVERSHOOTING_MULT, F_DOMAIN);
    float height = percentPos.y*OVERSHOOTING_MULT;
    float updraftNextDelta = (fel-flcl)*NEXT_UPDRAFT_SAMPLE;

    float dstxm = 1.0;
    float dstzm = 1.0;

    if(isEast(fLocalPos)){
        dstxm = 0.5;
    }

    if(isNorth(fLocalPos)){
        dstzm = 0.3;
    }

    float dist = length(fLocalPos.xz / vec2(dstxm, dstzm));
    float clipSize = mix(15000.0, 65000.0, square(clamp01(height)))*4.0;

    if(dist > clipSize){
        STOP
    }

    fel = mix(flcl, fel, stormStrength);
    float ael = mix(lcl, el, stormStrength);

    float heightWithin = (fLocalPos.y-flcl)/(fel-flcl);

    vec3 updraftOff = fromAngle(radians(59.0), 1.25)*stormStrength;
    vec3 updraftPos = updraftOff*5000.0*clamp01(heightWithin);
    updraftPos.y = fLocalPos.y;

    vec3 relUpdraft = updraftPos-fLocalPos;
    vec3 rotational = normalize(vec3(-relUpdraft.z, 0.0, relUpdraft.x));
    vec3 linear = normalize(vec3(relUpdraft.x, 0.0, relUpdraft.z));

    float angDist = sqrt(square(relUpdraft.x/1.15) + square(relUpdraft.z));
    float angSize = 5000.0 + angDist/4.0;
    float ang = radians(mix(250.0*clamp01(smoothStage-1.0), 400.0, clamp01(storm.windspeed/300.0)))*square(stormStrength)*(1.0-(disorg*0.2))*pow4(1.0-clamp01(angDist/angSize));
    ang *= square(1.0-clamp01(height));
    ang += radians(15.0);

    vec3 hookOffset = fromCardinal(-600.0, -600.0);
    vec2 rotated = rotatePoint(fLocalPos.xz, updraftPos.xz+(hookOffset.xz*(1.0-disorg)), ang);
    vec3 absRelPos = fLocalPos;
    fLocalPos = vec3(rotated.x, fLocalPos.y, rotated.y);

    float stormDist = length(fLocalPos.xz);

    vec3 nextUpdraftPos = updraftOff*5000.0*clamp01(heightWithin+NEXT_UPDRAFT_SAMPLE);
    nextUpdraftPos.y = fLocalPos.y+updraftNextDelta;

    vec3 updraftTopPos = (updraftOff/vec3(1.0, 1.0, 1.5))*5000.0;
    float distToUpdraft = distance(fLocalPos.xz, updraftPos.xz);

    relUpdraft = updraftPos-fLocalPos;
    vec3 relUpdraftTop = updraftTopPos-fLocalPos;

    float xM = 1.0;
    float zM = 1.0;

    float xMW = 0.3;
    float zMW = 0.8;

    if(eastOf(fLocalPos, updraftPos)){
        xM = 0.05;
        xMW = 1.0;
    }

    if(northOf(fLocalPos, updraftPos)){
        zM = 0.2;
        zMW = 0.3;
    }

    float shearedDistanceToUpdraft = sqrt((square(relUpdraft.x)*xM) + (square(relUpdraft.z)*zM));

    xM = 1.0;
    zM = 1.0;

    if(eastOf(fLocalPos, updraftTopPos)){
        xM = 0.05;
    }

    if(northOf(fLocalPos, updraftTopPos)){
        zM = 0.2;
    }

    float shearedDistanceToUpdraftTop = sqrt((square(relUpdraftTop.x)*xM) + (square(relUpdraftTop.z)*zM));
    float westShearedDistanceToUpdraft = sqrt((square(relUpdraftTop.x)*xMW) + (square(relUpdraftTop.z)*zMW));
    float bestDist = min(shearedDistanceToUpdraftTop, westShearedDistanceToUpdraft);

    float stormThick = fel-flcl;

    float topSize = 15000.0*stormStrength;
    float topThick = 6000.0*sqrt(1.0-clamp01(shearedDistanceToUpdraftTop/topSize))*stormStrength*4.0;
    topThick = min(topThick, stormThick/2.0);

    float minUpdraftWidth = 2500.0 + (storm.width*1.25);
    float updraftWidth = mix(minUpdraftWidth, mix(minUpdraftWidth, max(topSize, minUpdraftWidth), stormStrength), pow6(clamp01((fLocalPos.y-flcl)/((fel-topThick)-flcl))))*stormStrength;
    float overshootHeight = F_DOMAIN_Y*(OVERSHOOTING_MULT-1.0);

    float bottomSize = topSize/4.0;
    float bottomThick = 5000.0*square(1.0-clamp01(bestDist/bottomSize))*stormStrength*7.0;
    bottomThick = min(bottomThick, stormThick/2.0);

    float rainSizeMod = mix(0.85, 0.5, clamp01(heightWithin));

    rainSizeMod *= 1.0 + (values.noise4*0.4);

    float rotSpeed = 55.0+min(storm.windspeed, 150.0);

    float rainAdd = 1.0-clamp01(shearedDistanceToUpdraftTop/(rainSizeMod*topSize*pow5(stormStrength)));
    rainAdd *= cube(stormStrength);
    rainY = max(rainY, mix(rainY, ael, step(0.0, rainAdd)));
    precip += rainAdd;

    if(fLocalPos.y > fel-topThick && fLocalPos.y < fel){
        float p = 1.0-clamp01(shearedDistanceToUpdraftTop/(topSize*pow5(stormStrength)));
        float pFromTop = fel-fLocalPos.y;
        float pFromBot = fLocalPos.y-(fel-topThick);
        float v = pow9(p)*clamp01(pFromTop/(topThick/2.0))*clamp01(pFromBot/(topThick/2.0))*pow5(stormStrength)*0.00005;
        clouds += v*mix(1.0, 1.65, clamp01(-relUpdraft.x/10000.0));
        motion += ((linear*8.0)+vec3(0.0, -0.75, 0.0))*-50.0*pow1_4(v);
        motion += rotational*rotSpeed*pow1_4(v);
    }

    rainAdd = 1.0-clamp01(bestDist/(rainSizeMod*bottomSize));
    rainAdd *= 0.6;
    rainY = max(rainY, mix(rainY, lcl, step(0.0, rainAdd)));
    precip += rainAdd;

    if(fLocalPos.y > flcl && fLocalPos.y < flcl+bottomThick){
        float p = 1.0-clamp01(bestDist/bottomSize);
        float pFromTop = (flcl+bottomThick)-fLocalPos.y;
        float pFromBot = fLocalPos.y-flcl;
        float v = square(p)*pow9(stormStrength)*clamp01(pFromBot/500.0)*clamp01(pFromTop/2000.0)*0.2;
        clouds += v;
        motion += ((linear*8.0)+vec3(0.0, 0.75, 0.0))*-50.0*pow1_4(v)*0.2;
        motion += rotational*rotSpeed*pow1_4(v)*0.2;
    }

    if(shearedDistanceToUpdraft < updraftWidth && fLocalPos.y >= flcl){
        float p = 1.0-clamp01(shearedDistanceToUpdraft/updraftWidth);

        if(fLocalPos.y > fel){
            p = pow1_4(1.0-clamp01(shearedDistanceToUpdraft/mix(mix(updraftWidth, 2000.0, clamp01((fLocalPos.y-fel)/500.0)), 0.0, clamp01((fLocalPos.y-fel)/overshootHeight))));
        }

        float pFromTop = (fel+overshootHeight)-fLocalPos.y;
        float pFromBot = fLocalPos.y-flcl;

        float perc = square(p)*clamp01(pFromBot/500.0)*clamp01(pFromTop/2000.0)*sqrt(stormStrength);
        clouds = max(clouds, perc*square(clamp((fLocalPos.y-flcl)/(fel-flcl), 0.9, 1.0)));

        vec3 alongUpdraft = normalize(nextUpdraftPos-updraftPos);
        float v = 1.0-clamp01(heightWithin);

        motion += alongUpdraft*60.0*stormStrength*pow1_8(perc)*v*4.0;
        motion += rotational*rotSpeed*stormStrength*pow1_8(perc)*v*0.25;
    }

    clouds *= 1.0 + (clamp01(absRelPos.x/4000.0)*0.75);

    vec3 modAbsRelPos = absRelPos;

    float modAbsRelPosLen = length(modAbsRelPos.xz);
    float p = 3.0*clamp01(modAbsRelPosLen/500.0)*(1.0-clamp01(modAbsRelPosLen/1250.0))*(1.0-clamp01(fLocalPos.y/6000.0))*clamp01((fLocalPos.y-flcl)/500.0)*stormStrength;
    clouds += p*0.85*square(storm.rainIntensity);

    float absRelPosLen = length(absRelPos.xz);
    float distForAng = absRelPosLen*mix(1.0, 2.5, sqrt(clamp01(heightWithin)));

    ang = atan2(absRelPos.z, absRelPos.x);
    ang += PI;
    ang -= distForAng/750.0;

    float t = sin(ang)*stormStrength;
    t *= clamp01(distForAng/mix(1000.0, 50.0, clamp01(storm.windspeed/120.0)));
    t *= 1.0-clamp01(distForAng/10000.0);
    t = (t-0.5)*2.0;
    t *= 1.0-clamp01(fLocalPos.y/16000.0);
    t *= clamp01((fLocalPos.y-flcl)/1500.0);
    clouds += max(t, 0.0)*(1.0-(disorg*0.25))*0.2;

    float torDist = distance(storm.position.xz, rayPos.xz);

    p = sqrt(1.0-clamp01(torDist/700.0))*(1.0-clamp01(fLocalPos.y/6000.0))*clamp01((fLocalPos.y-flcl)/500.0)*square(stormStrength);

    rainAdd = sqrt(1.0-clamp01(torDist/(500.0*rainSizeMod)))*square(stormStrength)*0.4*(1.0-smoothstep(lcl, lcl+50.0, rayPos.y))*0.6;
    rainY = max(rainY, mix(rainY, lcl, step(0.0, rainAdd)));
    precip += rainAdd;

    clouds = max(clouds, p*0.1);

    clouds = mix(sqrt(clouds*1.1)*1.2, sqrt(clouds)*0.1, pow1_4(clamp01(heightWithin)));

    float sub = max(clouds-0.65, 0.0);
    clouds -= sub/1.5;

    p = 1.0-clamp01((dist-(clipSize-5000.0))/5000.0);
    clouds *= p;
    precip *= p;
    motion *= pow1_4(p);

    clouds *= sqrt(clamp01((fLocalPos.y-flcl)/2000.0));

    precip *= step(rayPos.y, rainY);
    precip *= 1.0-(clamp01(motion.y/25.0) * step(flcl, fLocalPos.y));
    precip *= 1.0-clamp01(clouds/0.2);

    float cutoff = 0.4 + (values.noise4*0.1);
    precip = max(precip-cutoff, 0.0);

    motion.y -= sqrt(precip)*100.0;

    return StormReturn(max(pow(clouds, 1.15)*1.6, 0.0), max(sqrt(precip), 0.0)*0.6*max(storm.rainIntensity, 0.2)*stormStrength, clamp(rainY, values.layer0height, CELL_HEIGHT+values.layer0height), motion);
}