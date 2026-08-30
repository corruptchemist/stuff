vec2 fromCardinal2(float north, float east){
    return vec2(east, -north);
}

vec3 fromCardinal(float north, float east){
    return vec3(east, 0.0, -north);
}

vec2 fromAngle2(float ang, float mag){
    return vec2(sin(ang), -cos(ang))*mag;
}

vec3 fromAngle(float ang, float mag){
    return vec3(sin(ang), 0.0, -cos(ang))*mag;
}

bool northOf(vec3 point, vec3 compare){
    return point.z < compare.z;
}

bool southOf(vec3 point, vec3 compare){
    return !northOf(point, compare);
}

bool eastOf(vec3 point, vec3 compare){
    return point.x > compare.x;
}

bool westOf(vec3 point, vec3 compare){
    return !eastOf(point, compare);
}

bool northOf(vec2 point, vec2 compare){
    return point.y < compare.y;
}

bool southOf(vec2 point, vec2 compare){
    return !northOf(point, compare);
}

bool eastOf(vec2 point, vec2 compare){
    return point.x > compare.x;
}

bool westOf(vec2 point, vec2 compare){
    return !eastOf(point, compare);
}

bool isNorth(vec3 vec){
    return northOf(vec, vec3(0.0));
}

bool isSouth(vec3 vec){
    return !isNorth(vec);
}

bool isEast(vec3 vec){
    return westOf(vec, vec3(0.0));
}

bool isWest(vec3 vec){
    return !isEast(vec);
}

bool isNorth(vec2 vec){
    return northOf(vec, vec2(0.0));
}

bool isSouth(vec2 vec){
    return !isNorth(vec);
}

bool isEast(vec2 vec){
    return westOf(vec, vec2(0.0));
}

bool isWest(vec2 vec){
    return !isEast(vec);
}