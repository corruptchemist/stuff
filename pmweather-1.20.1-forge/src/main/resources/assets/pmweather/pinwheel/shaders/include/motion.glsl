#define HALF_PI  1.5707963268
#define PI 3.14159265359
#define TWO_PI 6.28318530718

#define LOOP_TIME 600.0
#define MAGNITUDE 3.7283

struct MotionPositions{
    vec3 offsetPrev;
    vec3 offsetCur;
    float prevPerc;
    float curPerc;
};

float getPartial(float time){
    return fract(time / LOOP_TIME);
}

float getLongPartial(float time){
    return fract(time / (LOOP_TIME * 2.0));
}

float getMotionPerc(float x){
    return (cos((x+0.5)*TWO_PI)+1.0)/2.0;
}

float addNoises(float noisePrev, float noiseCur, MotionPositions offsets){
    return (noisePrev*offsets.prevPerc) + (noiseCur*offsets.curPerc);
}

MotionPositions getDynamicVectors(vec3 motion, float time){
    float partial = getPartial(time);
    float longPartial = getLongPartial(time);

    float s = floor(longPartial+0.5);

    vec3 prev = motion * (partial + s) * MAGNITUDE;
    vec3 cur = motion * (partial + (1.0 - s)) * MAGNITUDE;

    return MotionPositions(prev, cur, getMotionPerc(longPartial), getMotionPerc(longPartial+0.5));
}