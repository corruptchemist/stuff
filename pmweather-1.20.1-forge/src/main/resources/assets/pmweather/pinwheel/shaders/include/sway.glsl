#include pmweather:math
#include pmweather:cnoise

float getSinEffect(float hour, float mult, float offset){
    return (sin((hour*PI*mult)+(offset*PI))+1.0)/2.0;
}

float sinEffectFBM(float hour, float mult, int octaves, float offsetShift, float lacunarity){
    float offset = 0.0;
    float sine = 0.0;

    for(int i = 0; i < octaves; i++){
        offset += offsetShift;
        //mult *= lacunarity;
        sine += getSinEffect(hour, pow(mult, (float(i)/(5.0*lacunarity))+1.0), offset);
    }

    return sine/float(octaves+1);
}

vec3 getSwayOffset(float hour, vec3 wind, vec3 windRaw, float height, float realHeight){
    float windspeed = length(wind);
    vec3 offset = ((wind/windspeed)*pow(windspeed, 1.1))/80.0;
    float wPerc = clamp01(windspeed/150.0);
    offset *= mix(sinEffectFBM(hour, 50.0, 6, 0.2, 1.5), sinEffectFBM(hour, 300.0, 6, 0.2, 1.5), wPerc);
    offset.y += sin(hour*PI*30.0)*0.5*wPerc;
    offset *= height;

    vec3 heightOff = (windRaw / 10000.0)*cube(realHeight);
    heightOff.y -= square(length(heightOff.xz)/3.0);

    return offset + heightOff;
}