float hash(float p) {
    p = fract(p * .1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float cnoise(vec3 pos) {
    vec3 x = pos * 2.0;
    vec3 p = floor(x);
    vec3 f = fract(x);

    f       = f*f*(3.0-2.0*f);
    float n = p.x + p.y*57.0 + 113.0*p.z;

    return mix(mix(mix( hash(n+0.0), hash(n+1.0),f.x),
            mix( hash(n+57.0), hash(n+58.0),f.x),f.y),
            mix(mix( hash(n+113.0), hash(n+114.0),f.x),
                    mix( hash(n+170.0), hash(n+171.0),f.x),f.y),f.z);
}

int getOctavesFromQuality(int octaves, int quality){
    return octaves - int(floor(pow(4.0-quality, 0.75)));
}

float cfbm(vec3 x, int octaves, float lacunarity, float gain, float amplitude){
    float y = 0.0;

    for(int i = 0; i < max(octaves, 1); i++){
        float n = cnoise(x);
        n = (n-0.5)*2.0;

        float s = sign(n);
        n = abs(n);
        n *= n;
        n *= s;

        y += amplitude * n;
        x *= lacunarity;
        amplitude *= gain;
    }

    return y;
}

float cfbm01(vec3 x, int octaves, float lacunarity, float gain, float amplitude){
    return clamp(cfbm(x, octaves, lacunarity, gain, amplitude)+1.0, 0.0, 2.0)/2.0;
}