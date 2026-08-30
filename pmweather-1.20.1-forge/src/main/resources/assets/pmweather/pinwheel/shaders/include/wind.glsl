#define WIND_SCALE 250.0
#define WIND_UPSCALE 4.0
#define WIND_RES 64.0

vec3 getWind(vec3 data){
    vec3 wind = data;
    wind -= vec3(0.5);
    wind *= 2.0;
    wind = (wind*wind)*vec3(sign(wind.x), sign(wind.y), sign(wind.z));
    wind *= WIND_SCALE;

    return wind;
}

vec3 getWind(sampler2D localwind, vec3 position, vec3 offset){
    vec3 p = position + offset;
    vec2 uv = p.xz/WIND_UPSCALE;
    uv /= WIND_RES;
    uv += vec2(1.0);
    uv /= 2.0;

    if(uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec3(0.0);

    vec3 wind = texture(localwind, uv).xyz;
    return getWind(wind);
}

vec3 getWind(sampler2D localwind, sampler2D prevlocalwind, vec3 position, vec3 offset, vec3 prevoffset, float delta){
    return mix(getWind(prevlocalwind, position, prevoffset), getWind(localwind, position, offset), delta);
}