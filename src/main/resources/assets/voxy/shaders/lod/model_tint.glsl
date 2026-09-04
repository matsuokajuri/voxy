// Uses the existing RGBA8 blockModelAtlas; no tint bits are stored in texture alpha.
bool shouldApplyModelTint(uint tintingFunction, vec2 texturePos) {
    if (tintingFunction == 2u) {
        return true;
    }
    if (tintingFunction != 1u) {
        return false;
    }
    // The existing partial-tint contract classifies the base texel, not a mip average.
    // Explicit LOD also remains defined after helper-invocation / coverage early exits.
    vec3 tintTest = textureLod(blockModelAtlas, texturePos, 0.0).rgb;
    return abs(tintTest.r-tintTest.g) < 0.02 && abs(tintTest.g-tintTest.b) < 0.02;
}
