package me.cortex.voxy.forge;

record ForgeRealModelStoreFaceSample(
        String direction,
        boolean hasQuad,
        String spriteName,
        String spriteAtlas,
        String uvMin,
        String uvMax,
        int tintIndex,
        boolean hasTint,
        String cullDirection,
        int verticesLength,
        int encodedFaceDataWord
) {
    static ForgeRealModelStoreFaceSample empty(String direction) {
        return new ForgeRealModelStoreFaceSample(
                direction,
                false,
                "none",
                "none",
                "none",
                "none",
                -1,
                false,
                direction == null ? "none" : direction,
                0,
                -1
        );
    }
}
