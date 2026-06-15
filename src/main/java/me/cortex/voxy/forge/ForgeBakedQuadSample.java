package me.cortex.voxy.forge;

record ForgeBakedQuadSample(
        String quadDirection,
        int quadTintIndex,
        boolean quadHasTint,
        String quadSpriteName,
        String quadSpriteAtlas,
        String quadUvMin,
        String quadUvMax,
        String quadCullDirection,
        int quadVerticesLength,
        boolean hasAtlasSprite,
        boolean spriteUvReadable
) {
    static ForgeBakedQuadSample none() {
        return new ForgeBakedQuadSample(
                "none",
                -1,
                false,
                "none",
                "none",
                "none",
                "none",
                "none",
                0,
                false,
                false
        );
    }
}
