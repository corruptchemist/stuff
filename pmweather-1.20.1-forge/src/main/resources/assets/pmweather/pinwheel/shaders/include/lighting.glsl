float BeersLaw(float dist, float absorbtion){
    return exp(-dist * absorbtion);
}

float HenyeyGreenstein(float g, float mu){
    float denom = 1.0 + g * (g - 2.0 * mu);
    denom = sqrt(denom * denom * denom);
    float heyey = (1.0 - g * g) / (4.0 * 3.14 * denom);
    return mix(heyey, 1.0, 0.1);
}