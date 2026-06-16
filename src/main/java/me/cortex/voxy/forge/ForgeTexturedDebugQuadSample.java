package me.cortex.voxy.forge;

record ForgeTexturedDebugQuadSample(
        boolean ready,
        int textureId,
        int textureWidth,
        int textureHeight,
        int modelId,
        int blockStateId,
        String blockState,
        String sourceSprite,
        String sourceSpriteAtlas,
        int faceIndex,
        String faceName,
        int tileX,
        int tileY,
        String tile,
        String checksum
) {
    static ForgeTexturedDebugQuadSample missing(String reason) {
        return new ForgeTexturedDebugQuadSample(
                false,
                0,
                0,
                0,
                -1,
                -1,
                reason == null || reason.isBlank() ? "none" : reason,
                "none",
                "none",
                -1,
                "none",
                0,
                0,
                "none",
                "none"
        );
    }
}
