struct StormReturn{
    float clouds;
    float precip;
    float rainY;
    vec3 motion;
};

struct StormData{
    vec3 position;
    vec2 velocity;
    float smoothStage;
    float windspeed;
    float width;
    float touchdownSpeed;
    float tornadoShape;
    float rainIntensity;
    float stormSpin;
    int stormType;
    float occlusion;
    bool visualOnly;
    bool dying;
    bool tornadic;

    float depth;
};