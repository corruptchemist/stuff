#define HALF_PI  1.5707963268
#define PI 3.14159265359
#define TWO_PI 6.28318530718

vec2 rotatePoint(vec2 point, vec2 origin, float ang){
    vec2 p = point - origin;
    float x = (p.x*cos(ang))-(p.y*sin(ang));
    float y = (p.y*cos(ang))+(p.x*sin(ang));
    return vec2(x,y)+origin;
}

mat2 spin(float angle){
    return mat2(cos(angle),-sin(angle),sin(angle),cos(angle));
}

float distanceSqr(vec2 a, vec2 b){
    vec2 c = a-b;
    return dot(c,c);
}

vec2 nearestPoint(vec2 v, vec2 w, vec2 p){
    float l2 = distanceSqr(v, w);
    float t = clamp(dot(p-v, w-v) / l2, 0.0, 1.0);
    return v + t * (w - v);
}

float minimumDistance(vec2 v, vec2 w, vec2 p){
    float l2 = distanceSqr(v, w);
    if(l2 == 0.0) return distance(p, v);

    vec2 proj = nearestPoint(v,w,p);
    return distance(p, proj);
}

float atan2(float y, float x){
    return x == 0.0 ? sign(y)*HALF_PI : atan(y, x);
}

vec2 nearest(vec2 uv, float res){
    float unitSize = 1.0/res;
    vec2 off = mod(uv, unitSize) - vec2(unitSize / 2.0);
    return uv - off;
}

float remap(float value, float original_min, float original_max, float new_min, float new_max){
    return new_min + (value - original_min) / (original_max - original_min) * (new_max - new_min);
}

mat4 rotationMatrix(vec3 axis, float angle) {
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;

    return mat4(vec4(oc * axis.x * axis.x + c,           oc * axis.x * axis.y - axis.z * s,  oc * axis.z * axis.x + axis.y * s,  0.0),
            vec4(oc * axis.x * axis.y + axis.z * s,  oc * axis.y * axis.y + c,           oc * axis.y * axis.z - axis.x * s,  0.0),
            vec4(oc * axis.z * axis.x - axis.y * s,  oc * axis.y * axis.z + axis.x * s,  oc * axis.z * axis.z + c,           0.0),
            vec4(0.0,                                0.0,                                0.0,                                1.0));
}

vec3 rotate(vec3 v, vec3 axis, float angle) {
    mat4 m = rotationMatrix(axis, angle);
    return (m * vec4(v, 1.0)).xyz;
}

vec2 normalVec3ToEquirectangularUV(vec3 dir){
    return vec2(atan2(dir.x, -dir.z) / TWO_PI + 0.5, dir.y * 0.5 + 0.5);
}

vec2 normalVec3ToLambertUV(vec3 dir){
    return vec2((atan2(dir.x, -dir.z) / PI + 1.0) / 2.0, asin(dir.y) / PI + 0.5);
}

float boundedPercent(float lower, float upper, float x){
    return (x-lower)/(upper-lower);
}

float boundedPercent01(float lower, float upper, float x){
    return clamp(boundedPercent(lower, upper, x), 0.0, 1.0);
}

float getCosPerc(float x){
    return (-cos(x*PI)+1.0)/2.0;
}

float square(float x){
    return x*x;
}

float cube(float x){
    return x*x*x;
}

float pow4(float x){
    return square(square(x));
}

float pow5(float x){
    return pow4(x)*x;
}

float pow6(float x){
    return square(cube(x));
}

float pow9(float x){
    return cube(cube(x));
}

float pow1_4(float x){
    return sqrt(sqrt(x));
}

float pow1_3(float x){
    return pow1_4(pow(x, 1.333));
}

float pow3_4(float x){
    return cube(pow1_4(x));
}

float pow1_8(float x){
    return sqrt(pow1_4(x));
}

float clamp01(float x){
    return clamp(x, 0.0, 1.0);
}

vec3 worldPos(vec2 uv, float depth, mat4 invProj, mat4 viewmat){
    vec4 ndc;
    ndc.xy = (uv-0.5)*2.0;
    ndc.z = (depth-0.5)*2.0;
    ndc.w = 1.0;

    vec4 clip = invProj*ndc;
    vec4 view = viewmat*(clip/clip.w);
    vec3 result = view.xyz;

    return result;
}

float getDistFromDepth(float depth, vec2 uv, mat4 invProj, mat4 viewmat) {
    if (depth >= 1.0) return 1e10;
    vec3 viewPos = worldPos(uv, depth, invProj, viewmat);
    return length(viewPos);
}