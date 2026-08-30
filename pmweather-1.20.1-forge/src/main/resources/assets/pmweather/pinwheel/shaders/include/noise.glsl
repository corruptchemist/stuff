#define TEX_SIZE 512.0
#define TEX3D_SIZE 256.0

#include pmweather:cnoise

float noise2d(vec2 x, sampler2D noiseTex){
    x /= TEX_SIZE;
    return (texture(noiseTex, x, -1.0).r-0.5)*2.0;
}

float noise3d(vec3 x, sampler3D noiseTex){
    x /= TEX3D_SIZE;
    return (texture(noiseTex, vec3(0.0), -1.0).r-0.5)*2.0;
}

float noise(vec3 x, sampler2D noiseTex){
    x /= vec3(100.0,180.0,100.0)*3.0;

    x.y = fract(x.y)*512.0;
    float iz = floor(x.y);
    float fz = fract(x.y);
    vec2 a_off = vec2(23.0, 29.0)*(iz)/512.0;
    vec2 b_off = vec2(23.0, 29.0)*(iz+1.0)/512.0;
    float a = texture(noiseTex, x.xz + a_off, -1.0).r;
    float b = texture(noiseTex, x.xz + b_off, -1.0).r;
    return (mix(a,b,fz)-0.5)*2.0;
}

float tfbm3D(vec3 x, int octaves, float lacunarity, float gain, float amplitude, sampler3D noiseTex){
    float y = 0.0;

    for(int i = 0; i < max(octaves-1, 1); i++){
        y += amplitude * noise3d(x, noiseTex);
        x *= lacunarity;
        amplitude *= gain;
    }

    return y;
}

float tfbm(vec3 x, int octaves, float lacunarity, float gain, float amplitude, sampler2D noiseTex){
    float y = 0.0;

    for(int i = 0; i < max(octaves, 1); i++){
        y += amplitude * noise(x, noiseTex);
        x *= lacunarity;
        amplitude *= gain;
    }

    return y;
}